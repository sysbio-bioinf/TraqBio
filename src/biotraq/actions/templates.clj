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

(ns biotraq.actions.templates
  (:require
    [clojure.data :as data]
    [clojure.string :as str]
    [biotraq.db.crud :as crud]
    [biotraq.actions.tools :as t]
    [biotraq.actions.diff :as diff]))


(defn get-templates
  "Reads all templates and wrapps them in a response-map"
  []
  {:body (crud/read-templates)})


(defn get-template
  "Reads a template with templatesteps by id and wrapps it in a response-map"
  [id]
  (if-let [template (crud/read-template id)]
    {:status 200, :body template}
    {:status 500}))


(t/defaction create-template
  "Create a new template"
  {:description "Template \"{{parameters.template.name}}\" created",
   :error "Failed to create template \"{{parameters.template.name}}\"",
   :action-type :create}
  [template]
  (if (crud/create-template template)
    {:status 200}
    {:status 500}))





(t/defaction update-template
  "Update the data and steps of a template."
  {:description "Template \"{{parameters.data.template.name}}\" updated",
  ;:capture (load-project (:id project)),
  ;:message project-diff,
  :error "Update of template \"{{parameters.data.template.name}}\" failed",
  :action-type :update}
  [{new-template :template, old-template :oldtemplate :as data}]
  (let [template-diff (-> (data/diff (dissoc new-template :templatesteps) (dissoc old-template :templatesteps))
                        ; extract modifications of the new template (newest wins with respect to modified properties)
                        first
                         ; add template id to diff
                        (assoc :id (:id new-template))),
        step-changes (diff/step-modifications (:templatesteps new-template), (:templatesteps old-template))]
    (crud/update-template template-diff, step-changes)
    {:status 200, :body {}}))


(t/defaction delete-template
  "Delete the given template"
  {:description "Template \"{{captured.name}}\" deleted",
   :capture (crud/read-template template-id),
   :error "Failed to delete template with id \"{{parameters.templated-id}}\"",
   :action-type :delete}
  [template-id]
  (if (crud/delete-template template-id)
    {:status 200, :body {}}
    {:status 500}))