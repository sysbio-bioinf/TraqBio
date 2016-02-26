;; Copyright Fabian Schneider and Gunnar Völkel © 2014-2015
;;
;; Permission is hereby granted, free of charge, to any person obtaining a copy
;; of this software and associated documentation files (the "Software"), to deal
;; in the Software without restriction, including without limitation the rights
;; to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
;; copies of the Software, and to permit persons to whom the Software is
;; furnished to do so, subject to the following conditions:
;;
;; The above copyright notice and this permission notice shall be included in
;; all copies or substantial portions of the Software.
;;
;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
;; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
;; FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
;; AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
;; LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
;; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
;; THE SOFTWARE.

(ns biotraq.actions.project
  (:require
    [clojure.data :as data]
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.stacktrace :refer [print-cause-trace]]
    [clojure.tools.logging :as log]
    [cemerick.friend :as friend]
    [ring.util.http-response :as r]    
    [biotraq.config :as c]
    [biotraq.common :as common]
    [biotraq.db.crud :as crud]
    [biotraq.actions.mail :as mail]
    [biotraq.actions.tools :as t]
    [biotraq.actions.diff :as diff])
  (:import
    java.util.UUID))


(defn- render-tracking-link
  [tracking-nr]
  (str "http://" (c/tracking-server-domain) (c/server-location "/track/") tracking-nr))


(defn- render-edit-link
  [project-id]
  (str "http://" (c/tracking-server-domain) (c/server-location "/prj/edit/") project-id))





(defn normalize-email-list
  [emaillist]
  (some-> emaillist
    (str/split  #",")
    (->>
      (mapv str/trim)
      (str/join ", "))))


(defn user-notification-map->vector
  [filtered-state, user-map]
  (reduce-kv
    (fn [result, user, notify]
      (cond-> result (= notify filtered-state) (conj (name user))))
    []
    user-map))


(defn distinct-customers
  [customers]
  (let [customers (common/->vector customers)
        n (count customers)]
    (loop [i 0, customer-key-set #{}, unique []]
      (if (< i n)
        (let [customer (nth customers i)
              customer-key (crud/customer-key customer)]
          (recur
            (inc i),
            (conj customer-key-set customer-key),
            (cond-> unique
              (not (contains? customer-key-set customer-key))
              (conj (crud/normalize-customer-attributes customer)))))
        unique))))


(defn project-created-message
  [data-map]
  (when-let [customers (get-in data-map [:parameters, :project, :customers])]
    (if (== 1 (count customers))
      (format "for customer %s" (->> customers first :name))
      (format "for customers %s" (->> customers (map :name) (str/join ", "))))))


(t/defaction create-project
  "Creates a tracking number, sends an email to the customer and finally generates the project itsel.  "
  {:description "Project \"{{result.body.projectnumber}}\" created",
   :message project-created-message,
   :error "Project creation failed",
   :action-type :create}
  [{:keys [projectnumber] :as project}]
  (if (empty? (:customers project))
    {:status 400, :body {:error "Customer data is missing."}}
    (let [trackingNr (-> (UUID/randomUUID) (.toString)),
          projectnumber (if (str/blank? projectnumber) (crud/next-default-projectnumber) projectnumber),
          project (-> project
                    (set/rename-keys {:templatesteps :projectsteps})                    
                    (assoc
                      :dateofreceipt (t/now)
                      :trackingnr trackingNr
                      :trackinglink (render-tracking-link trackingNr)
                      :projectnumber projectnumber
                      :done 0)
                    (dissoc :id)
                    (update-in [:customers] distinct-customers)
                    (update-in [:notifycustomer] #(if (= % 1) 1 0))
                    (update-in [:additionalnotificationemails] normalize-email-list)
                    (update-in [:notifiedusers] (partial user-notification-map->vector 1))),
          project-id (crud/create-project project),
          mail? (c/send-mail?),
          ; send mail asynchronous (faster response on project creation), errors will be reported in the timeline by the mailing function
          ; We would only get errors about failed mail server configuration here anyway.
          send-mail-result (when mail?
                             (future
                               (mail/send-project-notification-mail
                                 (assoc project
                                   :id project-id
                                   :editlink (render-edit-link project-id)),
                                 (crud/user-email-addresses (:notifiedusers project)),
                                 :project-creation)))]          
          {:status 200 ; success, even on mail error since that is handled separately (otherwise the project creation would be reported as failed in the timeline)
           :body {:trackingnr trackingNr, :trackinglink (render-tracking-link trackingNr), :projectnumber projectnumber,
                  :customerinfos (mapv (fn [{:keys [name, email]}] (format "%s (%s)" name email)) (:customers project))}})))


(defn- process-timestamps
  [project-steps, now]
  (mapv
    (fn [{:keys [state, timestamp] :as step}]
      ; comment: application logic depending on property "timestamp" sent by UI :(
      (if (== state 1)
        ; set timestamp if unset
        (cond-> step (str/blank? timestamp) (assoc :timestamp now))
        ; remove timestamp if set
        (cond-> step (not (str/blank? timestamp)) (assoc :timestamp ""))))
    project-steps))


(defn- update-progress
  [{:keys [projectsteps] :as project}]
  (let [n (count projectsteps)
        finished (count (filter #(== 1 (:state %)) projectsteps))]
    (assoc project
      :step-count n
      :finished-step-count finished
      :done (if (== n finished) 1 0))))


(defn- process-project
  [project]
  (-> project
    (update-in [:projectsteps] process-timestamps (t/now))
    (update-in [:customers] distinct-customers)
    update-progress))


(def ^:private project-attributes
  {:flowcellnr "Flow Cell Number",
;   :customername "Customer Name",
;   :customeremail "Customer E-Mail",
   :description  "Description",
   :advisor "Advisor",
   :samplesheet "Sample Sheet",
   :orderform "Order Form"})

(defn changed-project-attributes
  [old-project, new-project]
  (keep
    #(when-not (t/equal? (% old-project) (% new-project))
       (% project-attributes))
    [:flowcellnr #_:customername #_:customeremail :description  :advisor :samplesheet :orderform]))


(defn project-diff
  [p+r-map]
  (let [new-project (process-project (get-in p+r-map [:parameters, :data :project])),
        old-project (process-project (get-in p+r-map [:parameters, :data :oldproject])),
        projectnumber-changed? (not (t/equal? (:projectnumber old-project) (:projectnumber new-project))),
        changed-attributes (seq (changed-project-attributes old-project, new-project)),
        completed?  (and (= 0 (:done old-project)) (= 1 (:done new-project))),
        customer-notification-changed? (and (not= (:notifycustomer new-project) (:notifycustomer old-project)))
        notifiedusers-delta (first (data/diff (:notifiedusers new-project) (:notifiedusers old-project)))
        added-users (user-notification-map->vector 1, notifiedusers-delta),
        removed-users (user-notification-map->vector 0, notifiedusers-delta),
        staff-notification-changed? (or (pos? (count added-users)) (pos? (count removed-users))),
        state-changes (->> (map #(= (:state %1) (:state %2)) (:projectsteps old-project) (:projectsteps new-project))
                        (remove true?)
                        count),
        changed-step-diff (diff/describe-modifications (diff/step-modifications (:projectsteps new-project), (:projectsteps old-project))),
        diff (cond-> []
               projectnumber-changed?
               (conj (format "Project \"%s\" has been renamed to \"%s\"." (:projectnumber old-project) (:projectnumber new-project)))
               completed?
                 (conj "Project has been completed.")
               (and (not completed?) (pos? state-changes))
                 (conj (format "The state of %s project steps has changed. (%s/%s completed)" state-changes, (:finished-step-count new-project), (:step-count new-project)))
               changed-attributes
                 (conj (format "Changed project attributes: %s" (str/join ", " changed-attributes)))
               customer-notification-changed?
                 (conj (if (= 1 (:notifycustomer new-project)) "The customer will be notified about project step completion." "The customer is not notified about project step completion anymore."))
               staff-notification-changed?
                 (conj (str "The notified staff has been changed:\n"
                         (str/join "\n"
                           (remove nil?
                             [(when (seq added-users)
                                (str "  added: " (str/join ", " added-users)))
                              (when (seq removed-users)
                                (str "  removed: " (str/join ", " removed-users)))]))
                         "."))
               (or projectnumber-changed? completed? (pos? state-changes))
                 (conj "")
               changed-step-diff
                 (into changed-step-diff))]
   (when (seq diff)
     (str/join "\n" diff))))


(defn load-project
  [project-id]
  (process-project (crud/read-project project-id)))


(defn filter2
  "Filter the elements of xs based on a predicate applied to the values of xs and ys at the same sequential position.
  If #xs > ys#, the funtion can filter at most #ys elements. The predicate is defined to be false for the remaining elements of xs."
  [pred, xs, ys]
  (let [xs (common/->vector xs),
        ys (common/->vector ys),
        nx (count xs),
        ny (count ys),
        n  (min nx, ny)]
    (loop [i 0, result (transient [])]
      (if (< i n)
        (let [x_i (nth xs i),
              y_i (nth ys i)]
          (recur
            (unchecked-inc i),
            (cond-> result (pred x_i, y_i) (conj! x_i))))
        (persistent! result)))))



(defn progress-information
  "Determines the progress information that can be included in notification e-mails."
  [new-project, old-project]
  (let [completed-steps (filter2
                          (fn [new-step, old-step]
                            (and
                              (== (:state new-step) 1)
                              (== (:state old-step) 0)))
                          (sort-by :id (:projectsteps new-project)),
                          (sort-by :id (:projectsteps old-project))),
        n (count completed-steps),
        step-information (->> completed-steps
                           (sort-by :sequence)
                           (mapv
                             (fn [{:keys [type, freetext]}]
                               (format "Step: %s\n%s", type, (if (str/blank? freetext) "" (str "\n  " (str/replace freetext "\n" "\n  ")))))))]
    (str/join "\n\n"
      (list*
        (format "The following %s been completed:" (if (< 1 n) (str n " steps have") "step has"))
        (cond-> step-information
          (== (:done new-project) 1) (conj "The project has been finished."))))))


(defn step-completion-notification
  [new-project, old-project]
  (try
    (let [notifycustomer (== (:notifycustomer new-project) 1),
          notifiedusers (user-notification-map->vector 1 (:notifiedusers new-project))]
      (when (or notifycustomer (seq notifiedusers))
        (mail/send-project-notification-mail
          (assoc new-project
            :progressinfo (progress-information new-project, old-project),
            :trackinglink (render-tracking-link (:trackingnr new-project))
            :editlink (render-edit-link (:id new-project))),
          (crud/user-email-addresses notifiedusers),
          :project-progress)))
    (catch Throwable t
      (log/errorf "Failed to determine step completion before sending the notification e-mails. Error:\n%s"
        (with-out-str (print-cause-trace t))))))


(defn completion
  [{:keys [finished-step-count, projectsteps]}]
  (/ (double finished-step-count) (count projectsteps)))


(defn customer-map
  [project]
  (let [customers (map-indexed #(assoc (crud/normalize-customer-data %2) :sequence %1) (:customers project))]
    (zipmap (map #(select-keys % [:name :email]) customers) customers)))


(defn customers-diff
  [new-project, old-project]
  (let [old-customer-map (customer-map old-project),
        [only-new-customers, only-old-customers] (data/diff (customer-map new-project), old-customer-map)]    
    (reduce
      (fn [result-map, name+email]
        (if (contains? only-new-customers name+email)
          (if (contains? only-old-customers name+email)
            ; customer has been modified (e.g. sequence)
            (update-in result-map [:modified-customers] conj (merge name+email (get only-new-customers name+email)))
            ; customer has been added
            (update-in result-map [:added-customers] conj (get only-new-customers name+email)))
          ; customer has been removed
          (update-in result-map [:removed-customers] conj (get only-old-customers name+email))))
      {:added-customers [],
       :removed-customers [],
       :modified-customers []}
      (distinct (concat (keys only-new-customers) (keys only-old-customers))))))


(t/defaction update-project
  "Updates the changes to a project. Marks a project as completed when all steps are completed."
  {:description "Project \"{{parameters.data.project.projectnumber}}\" updated",
   ;:capture (load-project (:id project)),
   :message project-diff,
   :error "Update of project \"{{parameters.data.project.projectnumber}}\" failed",
   :action-type :update}
  [{new-project :project, old-project :oldproject :as data}]
  (let [new-project (process-project new-project),
        old-project (process-project old-project),
        project-diff (-> (data/diff (dissoc new-project :projectsteps), (dissoc old-project :projectsteps))
                       ; extract modifications of the new project (newest wins with respect to modified properties)
                       first
                       ; add project id to diff
                       (assoc :id (:id old-project))
                       ; remove :notifiedusers since the diff is handled separately
                       (dissoc :notifiedusers)),
        step-changes (diff/step-modifications (:projectsteps new-project), (:projectsteps old-project))
        notifiedusers-delta (first (data/diff (:notifiedusers new-project) (:notifiedusers old-project))),        
        added-users (user-notification-map->vector 1, notifiedusers-delta),
        removed-users (user-notification-map->vector 0, notifiedusers-delta),
        {:keys [added-customers, removed-customers, modified-customers]} (customers-diff new-project, old-project),
        project-id (:id new-project),
        steps-completed? (> (completion new-project) (completion old-project))]    
    ; update of notified users must be handled here to determine the differences
    (if (and
         (crud/update-project project-diff, step-changes)
         (crud/add-customers project-id, added-customers)
         (crud/modify-customers project-id, modified-customers)
         (crud/remove-customers project-id, removed-customers)
         (crud/add-notified-user-for-project project-id, added-users)
         (crud/remove-notified-user-from-project project-id, removed-users))
      (do
        ; after everything has been update sent notification (if needed and specified)
        (when (and steps-completed? (c/send-mail?))
          (future (step-completion-notification new-project, old-project)))
        {:status 200})
      {:status 500})))


(defn- create-filename
  "Builds filename"
  [tmp-file-name nr]
  (str (c/upload-path) nr "/" tmp-file-name))


(defn upload
  "File upload. Needs multi-param-file and tracking number"
  [file nr]  
  (if (and file nr)
    (let [tmp-file (:tempfile file)
          new-file-name (create-filename (:filename file) nr)
          new-file (io/file new-file-name)]
      (.mkdirs (io/file (str (c/upload-path) nr)))
      (io/copy tmp-file new-file)
      {:status 200})
    {:status 500}))


(t/defaction delete-project
  "Delete Project with id"
  {:description "Project \"{{captured.projectnumber}}\" deleted",
   :capture (load-project id),
   :error "Failed to delete project \"{{captured.projectnumber}}\"",
   :action-type :delete}
  [id]
  (if (crud/delete-project id)
    {:status 200, :body {}}
    {:status 500}))
