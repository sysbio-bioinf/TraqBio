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

(ns traqbio.core
  (:require
    [clojure.string :as string]
    [clojure.stacktrace :refer [print-cause-trace]]
    [clojure.java.io :as io]
    [clojure.tools.cli :as cli]
    [clojure.tools.logging :as log]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.http-response :refer [catch-response]]
    [compojure.handler :as handler]
    [cemerick.friend :as friend]
    (cemerick.friend
      [workflows :as workflows]
      [credentials :as creds])
    [traqbio.config :as c]
    [traqbio.routes :as routes]
    [traqbio.db.crud :as crud]
    [traqbio.db.init :as init]
    [traqbio.db.migrate :as migrate]
    [traqbio.runtime :as runtime]
    [traqbio.actions.templates :as tmpl]
    [traqbio.actions.tools :as tools])
  (:gen-class))


(defn failed-login
  [{{:keys [username]} :params, remote-ip :remote-addr, :as request}]
  (log/errorf "Failed login attempt for user \"%s\" from \"%s\"", username, remote-ip)
  (workflows/interactive-login-redirect request))


; Enable authentication
(defn app
  [{:keys [port, ssl?, ssl-port, server-root] :as server-config}]
  (binding [friend/*default-scheme-ports* {:http port, :https ssl-port}]
    (handler/site
      (routes/wrap-uncaught-exception-logging
        (runtime/wrap-shutdown (cond-> (routes/use-server-root server-root, (routes/shutdown-routes))                              
                                 ssl? (friend/requires-scheme :https)),
          (catch-response
            (friend/authenticate
              (cond-> (routes/use-server-root server-root, (routes/app-routes))
                ssl? (friend/requires-scheme :https))
              {:allow-anon? true
               :credential-fn (partial creds/bcrypt-credential-fn crud/authentication-map)
               :default-landing-uri (c/server-location "/")
               :login-uri (c/server-location "/login")
               :login-failure-handler failed-login
               :unauthorized-handler routes/unauthorized-handler
               :workflows [(workflows/interactive-form)]})))))))

(def init-options
  [
    ["-a" "--admin NAME" "Name of the admin user"
     :default "admin"]
    ["-p" "--password SECRET" "Admins password"
     :default "traqbio"]
    ["-d" "--data-base-name NAME" "Name of the database. TraqBio will not override a existing database file."
     :default "traqbio.db"]
    ["-t" "--template-file NAME" "Path to file with a initial set of templates."]
    ["-h" "--help"]
  ])

(def run-options
  [
    ["-c" "--config-file FILENAME" "Path to the config file"
     :default "traqbio.conf"]
    ["-h" "--help"]
  ])

(def app-options [])

(defn app-usage []
  (->> ["Usage: traqbio action args"
        ""
        "Actions:"
        "  init             Initialize the TraqBio instance"
        "  run              Run TraqBio"
        "  export db file   Export given database to the specified file."
        "  import db file   Import data from given file into the specified data base."
        "  export-templates Export the template in the given database to the specified file."
        ""
        "For informations about args use:"
        "  traqbio init -h"
        "or"
        "  traqbio run -h"]
       (string/join \newline)))

(defn init-usage [summary]
  (->> ["Initialise the TraqBio instance."
        ""
        summary]
       (string/join \newline)))

(defn run-usage [summary]
  (->> ["Start the TraqBio instance with a given config file."
        ""
        summary]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))


(defn db-exists?
  [config-filename, db-filename]
  (if (.exists (io/file db-filename))
    true
    (let [msg (format
                (str
                  "The database file \"%s\" does not exist!\n"
                  "You have the following two options to fix that:\n"
                  "(1) Fix the path to the existing database file in the configuration \"%s\".\n"
                  "(2) Rerun the TraqBio initialisation.")
                (-> db-filename io/file .getAbsolutePath),
                (-> config-filename io/file .getAbsolutePath))]
      (println msg)
      (log/error msg)
      false)))


(defn read-config
  [config-file]
  (try
    ; we trust anyone with access to the config file, so just load it
    (load-file config-file)
    #_(read-string (slurp config-file))
    (catch Throwable t
      (let [log-file "startup-errors.log"]
        (binding [log/*force* :direct]
          (runtime/configure-logging {:log-level :info, :log-file log-file})
          (log/errorf "Error when reading config file \"%s\":\n%s"
            config-file
            (with-out-str (print-cause-trace t)))
          (exit 2
            (format "Error when reading config file \"%s\": \"%s\"\nFor details see \"%s\"."
              config-file
              (.getMessage t)
              log-file)))))))


(defn run
  "Run TraqBio"
  [& run-args]
  (let [{:keys [options errors summary]} (cli/parse-opts (first run-args) run-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (run-usage summary))
      (not (.exists (clojure.java.io/as-file (:config-file options)))) (exit 1 "Config file missing")
      errors (exit 1 (error-msg errors)))
    (let [{:keys [data-base-name, server-config, upload-path, mail-config] :as config} (read-config (:config-file options))]
      (runtime/configure-logging config)
      (c/update-config  config)
      (c/update-db-name data-base-name)      
      (when (db-exists? (:config-file options), data-base-name)
        ; Start server (query server-config atom, since default settings might be missing in the config read from the file)
        (let [server (runtime/start-server (app (c/server-config)))]
          (runtime/shutdown-on-sigterm!)
          server)))))


(defn add-sequence-number
  [template-steps]
  (when (seq template-steps)
    (mapv
      (fn [step, idx]
        (assoc step :sequence (inc idx)))
      template-steps
      (range))))


(defn create-template
  [template]
  (-> template
    (update-in [:templatesteps] add-sequence-number)
    tmpl/create-template))



(defn init
  "Init TraqBio"
  [& init-args]
  (let [{:keys [options errors summary]} (cli/parse-opts (first init-args) init-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (init-usage summary))
      errors (exit 1 (error-msg errors)))
    ;; Create default conf
    (when-not (.exists (clojure.java.io/as-file "traqbio.conf"))
      (c/write-config-file "traqbio.conf", (dissoc options :password :admin :template-file)))
    ;; create db
    (when (init/create-database-if-needed (:data-base-name options))
      ; database had to be created, add admin user
      (crud/put-user {:username (:admin options), :password (:password options) :role ::c/configadmin})
      (crud/insert-log {:success 1, :date (tools/now), :type "create", :action "TraqBio instance created."}))
    (c/update-db-name (:data-base-name options))
    (when-let [template-file (:template-file options)]
      (let [templates (read-string (slurp template-file))]
        (doseq [tmpl templates]
          (create-template tmpl))))))


(defn -main[& args]
  (case (first args)
    "init" (init (rest args))
    "run" (run (rest args))
    "export-templates" (let [[db-filename, export-filename] (rest args)]
                         (migrate/export-templates db-filename, export-filename))
    "export" (let [[db-filename, export-filename] (rest args)]
               (migrate/export-data db-filename, export-filename))

    "import" (let [[db-filename, import-filename] (rest args)]
               (migrate/import-data db-filename, import-filename))
    (exit 1 (app-usage))))