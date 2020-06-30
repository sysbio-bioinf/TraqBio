;; Copyright Fabian Schneider and Gunnar Völkel © 2014-2020
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

(ns traqbio.templates
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]
    [selmer.parser :as parser]
    [selmer.filters :as sel-filter]
    [cemerick.friend :as friend]
    [traqbio.config :as c]
    [traqbio.db.crud :as crud]
    [traqbio.version :as v]
    [clojure.data.json :as json]))

;; Filter functions for selmer (templating)
(sel-filter/add-filter! :even (fn [counter] (even? counter)))

(sel-filter/add-filter! :one (fn [value] (some-> value (= 1))))


(defn ifnotify-handler
  "Selmer tag with arguments [project, user] that checks whether the user shall be notified for the project."
  [args context-map content]
  (let [[project, user] (->> args
                          (mapv
                            (fn [arg]
                              (or
                                (->> arg keyword (get context-map))
                                arg))))]
    (if (and (map? project) (string? user) (= 1 (get-in project [:notifiedusers, user])))
      (get-in content [:ifnotify, :content])
      (get-in content [:else, :content]))))


(parser/add-tag! :ifnotify #'ifnotify-handler :else :endifnotify)


(sel-filter/add-filter! :signal-color
  (fn [action-type]
    (case action-type
      "create" "success"
      "delete" "warning"
      "update" "info"
      "default")))

(defn- sort-projectsteps
  "Sorsts the template steps by sequence"
  [project]
  (let [steps (:projectsteps project)]
    (assoc project :projectsteps (vec (sort-by :sequence steps)))))

(defn- process-project
  "Adds progess information to project"
  [project]
  (let [project (assoc project :customernames (->> project :customers (map :name) (str/join ", ")))
        done (count (filter #(= 1 (:state %)) (:projectsteps project)))
        all (count (:projectsteps project))]
    (if (> all 0)
      (assoc project :processed (* 100 (double (/ done all))) :k done :n all)
      project)))

(defn- index-of-current
  "Counts index of current project-step of project"
  [steps]
  (when-let [done (take-while #(= 1 (:state %)) steps)]
    (count done)))

(defn- detect-current-step
  "Adds index of current project-step to project"
  [project]
  (let [idx (index-of-current (:projectsteps project))
        all (count (:projectsteps project))]
    (if (< idx all)
      (assoc-in project [:projectsteps idx :isCurrent] true)
      project)))

(defn- add-auth-info
 "Adds authentification to project"
 [request]
 (let [auth (friend/current-authentication)]
   (assoc request
     :userLogin (:username auth),
     :isAdmin (contains? (:roles auth) ::c/admin),
     :isConfigAdmin (contains? (:roles auth) ::c/configadmin),
     :isAuthenticated (not (nil? auth)))))

(defn add-server-root
  [m]
  (let [root (c/server-root)
        root (cond->> root (not (str/blank? root)) (str "/"))
        page-title (c/page-title)
        page-title-link (c/page-title-link)
        develop? (c/develop?)
        admin-shutdown? (c/admin-shutdown?)]
    (cond-> (assoc m :serverRoot root, :version (v/traqbio-version))
      page-title (assoc :pageTitle page-title)
      page-title-link (assoc :pageTitleLink page-title-link)
      develop? (assoc :develop develop?)
      admin-shutdown? (assoc :adminShutdown admin-shutdown?))))


;; Functions to render templates

(defn not-found
  []
  (parser/render-file "templates/notfound.html" (add-server-root {})))


(defn error
  []
  (parser/render-file "templates/error.html" (add-server-root {})))


(defn login [request]
  (parser/render-file "templates/login.html"
    (add-server-root {:request (add-auth-info request)
                      :resetlink (str "http://" (c/tracking-server-domain) (c/server-location "/resetrequest"))}) ))


(defn reset-request-view
  ([request]
    (reset-request-view request, nil))
  ([request, {:keys [missing-data?, reset-requested?]}]
    (parser/render-file "templates/resetrequest.html"
      (add-server-root {:request
                        (update-in request [:params]
                          #(cond-> %
                             reset-requested? (assoc :reset_requested true)
                             missing-data?    (assoc :reset_missingdata true)))}))))


(defn reset-password-view
  ([reset-data]
    (reset-password-view reset-data, {:reset-completed? false}))
  ([{:keys [username, resetrequestid] :as reset-data}, {:keys [successful-reset?, reset-completed?, error-message]}]
    (parser/render-file "templates/reset.html"
      (add-server-root {:reset {:username username,
                                :success successful-reset?
                                :error error-message
                                :completed reset-completed?,
                                :requestid resetrequestid} }))))


(def ^:private ^:constant timeline-entries-per-page 20)

(defn timeline [request]
  (let [page-str (get-in request [:params, :page]),
        page (or
               (when-not (str/blank? page-str)
                 (try (Long/parseLong ^String page-str) (catch Exception e nil)))
               1),
        log-count (crud/log-count),
        page-count (cond-> (quot log-count timeline-entries-per-page)
                     (pos? (mod log-count timeline-entries-per-page)) inc),
        page (cond
               (< page 1) 1,
               (> page page-count) page-count,
               :else page)]
    (parser/render-file "templates/timeline.html"
      (add-server-root
        {:request (add-auth-info request),
         :log (crud/read-log (* (dec page) timeline-entries-per-page), timeline-entries-per-page),
         :activepage page,
         :prevpage (max (dec page), 1),
         :nextpage (min (inc page), page-count),
         :pages (vec (range 1 (inc page-count)))}))))


(defn user-list
  [request users]
  (parser/render-file "templates/userlist.html"
    (add-server-root {:request (add-auth-info request) :users users})))

(defn get-user
  [request user]
  (parser/render-file "templates/user.html"
    (add-server-root {:request (add-auth-info request) :user user})))


(defn template-list
  [request, template-coll]
  (parser/render-file "templates/templatelist.html"
    (add-server-root
      {:request (add-auth-info request)
       :templates (vec (sort-by :name template-coll))})))


(defn template-create
  [request templates]
  (parser/render-file "templates/templatecreate-new.html"
    (add-server-root
      {:request (add-auth-info request)
       :templates templates})))


(defn template-edit
  [request, template]
  (parser/render-file "templates/templateedit-new.html"
    (add-server-root
      {:request (add-auth-info request)
       :templateData (-> template sort-projectsteps json/write-str)})))


(defn project-edit-list
  [request projects]
  (parser/render-file "templates/projecteditlist.html"
    (add-server-root
      {:request (add-auth-info request)
       :projects (mapv
                   #(-> % sort-projectsteps process-project)
                   projects)})))

(defn project-edit
  [request project]
  (parser/render-file "templates/projectedit.html"
    (add-server-root
      {:request (add-auth-info request)
       :userlist (mapv :username (filter :email (crud/read-users)))
       :project (-> project
                    sort-projectsteps
                    process-project
                    detect-current-step)
       :upload-path (c/upload-path)
       :customers (crud/read-customers)})))

(defn finished-project-list
  [request projects]
  (parser/render-file "templates/finishedprojectlist.html"
    (add-server-root
      {:request (add-auth-info request) :projects (mapv process-project projects)})))

(defn finished-project
  [request project]
  (parser/render-file "templates/finishedproject.html"
    (add-server-root
      {:request (add-auth-info request) :project (sort-projectsteps project)})))

(defn project-create
  [request templates]
  (parser/render-file "templates/projectcreate.html"
    (add-server-root
      {:request (add-auth-info request)
       :userlist (mapv :username (filter :email (crud/read-users)))
       :templates templates
       :customers (crud/read-customers)})))

(defn tracking
  [request, tracking-nr]
  (let [project (crud/read-project-by-tracking-nr tracking-nr)]
    (parser/render-file "templates/tracking.html"
      (add-server-root
        {:trackingnr tracking-nr
         :request (add-auth-info request),
         :project (-> project
                    sort-projectsteps
                    process-project
                    detect-current-step),
         :upload-path (c/upload-path)}))))

