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

(ns biotraq.routes
  (:require
    [clojure.string :as str]
    [ring.util.response :as response]
    [ring.middleware.json :as json]
    [ring.middleware.multipart-params :as mp]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.not-modified :refer [wrap-not-modified]]
    [compojure.core :as core]
    [compojure.route :as route]
    [cemerick.friend :as friend]
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [clojure.stacktrace :refer [print-cause-trace]]
    [biotraq.templates :as templates]
    [biotraq.api :as api]
    [biotraq.db.crud :as db]
    [biotraq.actions.user :as user-action]
    [biotraq.actions.project :as project-action]
    [biotraq.config :as c]
    [biotraq.actions.templates :as templ-api]
    [biotraq.runtime :as runtime])
  (:gen-class))


(defn shutdown-page
  []
  {:status  503
   :headers {"Content-Type" "text/html"}
   :body (parser/render-file "templates/shutdown.html" (templates/add-server-root {}))})

; Define routes
(core/defroutes system-routes
  (core/POST "/stop" req
    (runtime/stop-server)
    (shutdown-page)))

(core/defroutes user-routes
  (core/GET "/" request (templates/user-list request (db/read-users)))
  (core/PUT ["/:name"] [name :as request] (user-action/update-user (:body request)))
  (core/POST "/" request (user-action/create-user (:body request)))
  (core/DELETE ["/:name"] [name] (user-action/delete-user name))
  (core/GET ["/:name"] [name :as request] (templates/get-user request (db/read-user name))))


(core/defroutes project-routes
  (core/context "/edit" []
    (core/GET "/" request (templates/project-edit-list request (db/read-current-projects)))
    (core/GET ["/:id", :id #"[0-9]+"] [id :as request]
              (if-let [project (db/read-project id)]
                (if (= (:done project) 1)
                  (ring.util.response/redirect (c/server-location (format "/prj/view/%s" id)))
                  (templates/project-edit request, project))
                (templates/not-found)))
    (core/PUT ["/:id", :id #"[0-9]+"] [id :as request] (project-action/update-project (:body request)))
    (core/DELETE ["/:id", :id #"[0-9]+"] [id] (project-action/delete-project id)))
  (core/context "/view" []
    (core/GET "/" request (templates/finished-project-list request (db/read-finished-projects)))
    (core/GET ["/:pId", :pId #"[0-9]+"] [pId :as request]
      (templates/finished-project request (db/read-project pId))))
  (core/context "/create" []
    (core/GET "/" request (templates/project-create request (db/read-templates)))
    (core/POST "/" request (project-action/create-project (:body request)))
    (mp/wrap-multipart-params
      (core/POST "/upload" {params :params} (project-action/upload (:file params) (:trackingnr params))))))


(core/defroutes template-api-routes
  (core/GET ["/:tmplId", :tmplId #"[0-9]+"] [tmplId]
    (templ-api/get-template tmplId))
  (core/PUT ["/:tmplId", :tmplId #"[0-9]+"] [tmplId :as request]
    (templ-api/update-template (:body request)))
  (core/DELETE ["/:tmplId", :tmplId #"[0-9]+"] [tmplId]
    (templ-api/delete-template tmplId))
  (core/context "/edit" []
    (core/GET ["/:tmplId", :tmplId #"[0-9]+"] [tmplId :as request]
      (if-let [template (db/read-template tmplId)]
        (templates/template-edit request, template)
        (templates/not-found))))
  (core/context "/create" []
    (core/GET "/" request
      (templates/template-create request (db/read-templates)))
    (core/POST "/" request
      (templ-api/create-template (:body request))))
  (core/GET "/list" request (templates/template-list request (db/read-templates))))


(core/defroutes api-routes
  (core/context "/prj" []
    (friend/wrap-authorize project-routes, #{::c/user}))
  (core/context "/template" []
    (friend/wrap-authorize template-api-routes, #{::c/user}))
  (core/context "/usr" []
    (friend/wrap-authorize user-routes, #{::c/admin}))
  (core/context "/system" []
    (friend/wrap-authorize system-routes, #{::c/admin})))


(core/defroutes script-api-routes
  (core/context "/api" []
    (friend/wrap-authorize
      (core/routes
        (core/GET "/active-project-list" []
          (api/active-projects))
        (core/context "/project/:project-id" [project-id]
          (core/GET "/is-active-step" request
            (api/is-active-step project-id, request))
          (core/GET "/sample-sheet" []
            (api/sample-sheet project-id))
          (core/POST "/finish-step/:step-id" [step-id :as request]
            (api/finish-step project-id, step-id, request))))
      #{::c/user})))


(defn wrap-cache-control
  [handler]
  (fn [request]
    (let [response (handler request)]
      (some-> response
        (update-in [:headers]
          #(merge {"Cache-Control" "max-age=60, must-revalidate"} %))))))


; Compose routes
(defn app-routes
  []
  (core/routes
    (core/GET ["/track/:trackingNr"] [trackingNr :as request] (templates/tracking request, trackingNr))
    (wrap-cache-control (wrap-not-modified (wrap-content-type (route/resources "/"))))
    (core/GET "/resetrequest" request (templates/reset-request-view request))
    (core/POST "/resetrequest" request (templates/reset-request-view request, 
                                         (merge 
                                           {:reset-requested? true}
                                           (user-action/request-password-reset request))))
    (core/GET ["/reset/:resetId", :resetId  #"[-0-9A-F]+"] [resetId :as request]
      (when-let [reset-data (db/password-reset-data-by-id resetId)]
        (templates/reset-password-view reset-data)))
    (core/POST ["/reset/:resetId", :resetId  #"[-0-9A-F]+"], [resetId :as request]
      (when-let [reset-data (db/password-reset-data-by-id resetId)]        
        (templates/reset-password-view reset-data,
          (assoc (user-action/reset-password request, reset-data)
            :reset-completed? true))))
    (core/GET "/login" request (templates/login request))
    (friend/logout (core/ANY "/logout" request (ring.util.response/redirect (c/server-location "/"))))
    (core/context "/timeline" []
      (friend/wrap-authorize
        (core/routes
          (core/GET "/" request (templates/timeline request))),
        #{::c/user}))  
    (core/context "/doc" []
      (friend/wrap-authorize
        (core/routes
          (core/GET "/:trackingnr/:filename"
            [trackingnr filename]
            (response/file-response (str (c/upload-path) trackingnr "/" filename)))),
        #{::c/user}))
    (json/wrap-json-response
      (json/wrap-json-body api-routes {:keywords? true}))
    script-api-routes
    (core/GET "/" request (ring.util.response/redirect (c/server-location "/timeline")))
    (route/not-found (templates/not-found))))


(defn unauthorized-handler
  [req]
  {:status  401
   :headers {"Content-Type" "text/html"}
   :body (parser/render-file "templates/unauthorized.html" (templates/add-server-root {}))})


(defn shutdown-routes
  []
  (core/routes
    (route/resources "/")
    (route/not-found (shutdown-page))))


(defn use-server-root
  "If a server root directory is given, then use this as prefix for all routes."
  [server-root, routes]
  (if (str/blank? server-root)
    routes
    (core/routes
      (core/context (c/server-location "", true) [] routes)
      (route/not-found (templates/not-found)))))



(defn wrap-uncaught-exception-logging
  "The given handler will be wrapped in a try catch that logs all exceptions."
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable t
        (let [{:keys [uri, request-method]} request]
          (log/errorf "Caught exception for request \"%s %s\":\n%s"
            (some-> request-method name str/upper-case)
            uri
            (with-out-str (print-cause-trace t)))
          {:status 500,
           :headers {"Content-Type" "text/html"}
           :body (templates/error)})))))