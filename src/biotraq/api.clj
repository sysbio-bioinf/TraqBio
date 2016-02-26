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

(ns biotraq.api
  (:require
    [clojure.string :as str]
    [ring.util.response :as response]
    [biotraq.config :as c]
    [biotraq.db.crud :as crud]
    [biotraq.actions.tools :as t]
    [biotraq.actions.project :as p]))



(defn active-projects
  []
  (str (str/join "\n" (crud/read-current-project-ids)) "\n"))


(defn first-unfinished-step
  [project-steps]
  (->> project-steps
    (sort-by :sequence)
    (filter #(zero? (:state %)))
    first))


(defn is-active-step
  [project-id, {{:keys [name]} :params}]
  (if-let [{:keys [projectsteps, projectnumber, done]} (crud/read-project project-id)]
    (if-let [{:keys [type, id]} (when (zero? done) (first-unfinished-step projectsteps))]
      (if (= type name)
        (str id)
        {:status 404 :body (format "Step \"%s\" is not the active step of project \"%s\"." name, projectnumber)})
      {:status 400 :body (format "Project \"%s\" has been finished meanwhile." projectnumber)})
    {:status 400 :body (format "There is no of project with id \"%s\"." project-id)}))


(defn sample-sheet
  [project-id]
  (let [{:keys [samplesheet, trackingnr, projectnumber] :as project} (crud/read-project project-id)]
    (if (str/blank? samplesheet)
      {:status 404, :body (format "Project \"%s\" has no samplesheet." projectnumber)}
      (response/file-response (str (c/upload-path) trackingnr "/" samplesheet)))))


(t/defaction project-step-finished
  "Updates the changes to a project. Marks a project as completed when all steps are completed."
  {:description "Step \"{{parameters.old-step.type}}\" of project \"{{parameters.old-project.projectnumber}}\" finished",
   :message "Automatic update via script API."
   :error "Finishing step \"{{parameters.old-step.type}}\" of project \"{{parameters.old-project.projectnumber}}\" failed",
   :action-type :update}
  [old-project, {:keys [sequence] :as old-step}, data-map]
  (let [new-step-data (reduce 
                        (fn [m, attribute]
                          (let [value (get data-map attribute)]
                            (cond-> m
                              (not (str/blank? value)) (assoc attribute value))))
                        {:state 1}
                        [:freetext, :description, :advisor]),
        new-project (update-in old-project [:projectsteps (dec sequence)] merge new-step-data)]
    (p/update-project {:project new-project, :oldproject old-project})))


(defn finish-step
  [project-id, step-id, {{:keys [freetext, description, advisor] :as data-map} :params}]
  (if-let [{:keys [state] :as old-step} (crud/step-exists project-id, step-id)]
    (if (zero? state)
      (project-step-finished (crud/read-project project-id), old-step, data-map)
      {:status 400 :body (format "The step with id \"%s\" of the project with id \"%s\" has already been marked as done." step-id, project-id)})
    {:status 400 :body (format "There is no step with id \"%s\" of the project with id \"%s\"." step-id, project-id)}))