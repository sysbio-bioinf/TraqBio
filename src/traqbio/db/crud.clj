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

(ns traqbio.db.crud
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as log]
    [cemerick.friend.credentials :as creds]
    [traqbio.config :as c]
    [traqbio.common :as common]))



(defn db-insert!
  [db-conn & args]
  (jdbc/with-db-transaction [conn db-conn] (apply jdbc/insert! conn args)))

(defn db-update!
  [db-conn & args]
  (jdbc/with-db-transaction [conn db-conn] (apply jdbc/update! conn args)))

(defn db-delete!
  [db-conn & args]
  (jdbc/with-db-transaction [conn db-conn] (apply jdbc/delete! conn args)))


(def rowid_keyword (keyword "last_insert_rowid()"))
(def max-id-keyword (keyword "max(id)"))

(defn capitalize-name
  [username]
  (when username
    (->> (str/split username #" ")
      (map #(->> (str/split % #"-") (map str/capitalize) (str/join "-")))
      (str/join " "))))

(defn lower-case
  [s]
  (when s
    (str/lower-case s)))

(defn has-user?
  "Exists given user in database?"
  [user-name]
  (boolean
    (seq
      (jdbc/query
        (c/db-connection)
        ["select username from user where username = ?" user-name]))))

(defn- encrypt-password
  [user-map]
  (if-let [password (user-map :password)]
    (assoc user-map :password (creds/hash-bcrypt password))
    user-map))

(defn put-user
  "Inserts or updates a user.
  user-map needs to contain :username, :password, :role and :email.
  :username is also the primary key. Password is safed as bcypt hash."
  [user-map]
  (when-let [user-name (:username user-map)]
    (let [user (encrypt-password user-map)]
      (if (has-user? user-name)
        (db-update!
          (c/db-connection) :user user ["username = ?" user-name])
        (db-insert!
          (c/db-connection) :user user)))))


(defn- all-roles
  [role]
  (loop [roles #{role}, check-coll [role]]
    (if (zero? (count check-coll))
      roles
      (if-let [parent-roles (some-> check-coll peek parents)]
        (recur (into roles parent-roles), (-> check-coll pop (into parent-roles)))
        (recur roles, (pop check-coll))))))

(defn- add-role-keywords
  [user-coll]
  (let [user-map (first user-coll)]
      (if-let [role (some-> user-map :role edn/read-string)]
        (assoc user-map :role role, :roles (all-roles role))
        user-map)))

(defn- read-full-user
  "Returns a full user-map and applys a funtrion to it"
  [user-name fn]
  (jdbc/query
    (c/db-connection) ["SELECT * FROM user WHERE username = ?" user-name] :result-set-fn fn))

(defn authentication-map
  "Returns the Authentification map for a given user"
  [user-name]
  (read-full-user user-name add-role-keywords))

(defn read-user
  "Returns a user-map"
  [user-name]
  (read-full-user user-name #(dissoc (first %) :password)))

(defn read-users
  "Returns a collection of user-maps"
  []
  (jdbc/query (c/db-connection) ["SELECT username, email, fullname, role FROM user"]))

(defn user-email-addresses
  [user-list]
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (mapv #(first (jdbc/query t-conn  ["SELECT username, fullname, email FROM user WHERE username = ?" %])) user-list)))

(defn delete-user
  [user-name]
  (db-delete!
    (c/db-connection)
    :user ["username = ?" user-name]))


(defn notified-users-of-project
  ([project-id]
    (notified-users-of-project (c/db-connection), project-id))
  ([db-conn, project-id]
    (mapv :username (jdbc/query db-conn, ["SELECT username FROM usernotification WHERE projectid = ?" project-id]))))


(defn add-notified-user-for-project
  ([project-id, usernames]
    (add-notified-user-for-project (c/db-connection), project-id, usernames))
  ([db-conn, project-id, usernames]
    (when (seq usernames)
      (apply db-insert! db-conn, :usernotification, (mapv #(hash-map :projectid project-id, :username %) usernames)))
    true))


(defn remove-notified-user-from-project
  ([project-id, usernames]
    (remove-notified-user-from-project (c/db-connection), project-id, usernames))
  ([db-conn, project-id, usernames]
    (when (seq usernames)
      (jdbc/with-db-transaction [t-conn db-conn]
        (doseq [user usernames]
          (jdbc/delete! t-conn, :usernotification, ["projectid = ? AND username = ?" project-id, user]))))
    true))


(defn read-templates
  "Get all templates"
  []
  (jdbc/query
    (c/db-connection)
    ["SELECT * FROM template"]))

(defn- read-template-steps
  [template-id]
  (jdbc/query
    (c/db-connection)
    ["SELECT templatestep.id, templatestep.type, templatestep.description, templatestep.sequence
      FROM templatestep
      WHERE templatestep.template = ?" template-id]))

(defn read-template
  "Returns a template-map with joind templatesteps"
  [template-id]
  (when-let [template (jdbc/query
                        ; cant use a join here, because jdbc/query results in a flat vector and strugles with double keywords
                        (c/db-connection) ["SELECT template.id, template.name, template.description
                                FROM template
                                WHERE template.id = ?" template-id] :result-set-fn first)]
    (assoc template
      :templatesteps (vec (sort-by :sequence (read-template-steps template-id))))))


(defn- update-or-insert!
  "Updates columns or inserts a new row in the specified table.
  See http://clojure-doc.org/articles/ecosystem/java_jdbc/using_sql.html#updating-or-inserting-rows-conditionally"
  [table row where-clause]
  (jdbc/with-db-transaction
    [t-con (c/db-connection)]
    (let [result (jdbc/update! t-con table row where-clause)]
      (if (zero? (first result))
        (jdbc/insert! t-con table row)
        result))))


(defn read-template-rowid
  "Rowid and id may differ. So use this function to read a template right after creation."
  [rowid]
  (->
    (jdbc/query (c/db-connection) ["SELECT id FROM template WHERE rowid=?" rowid] :result-set-fn #(-> % first :id))
    read-template))


(defn normalize-templatestep-data
  [templatestep]
  (select-keys templatestep [:id :type :description :sequence :template]))


(defn create-templatestep
  "Inserts or updates a templatestep.
  step must contain only :id :type :description :sequence :template
  See create-template-step-table"
  [t-conn, step]
  (jdbc/insert! t-conn, :templatestep, (-> step normalize-templatestep-data (dissoc :id))))


(defn update-templatestep
  [t-conn, {:keys [id] :as step}]
  (jdbc/update! t-conn, :templatestep, (normalize-templatestep-data step), ["id = ?", id]))


(defn delete-templatestep
  [t-conn, {:keys [id]}]
  (jdbc/delete! t-conn, :templatestep, ["id = ?", id]))


(defn normalize-template-data
  [template]
  (select-keys template [:id :name :description]))


(defn create-template
  "Inserts or updates a template.
  template must only contain :id :name :description :advisor :customeremail :customername :flowcellnr :templatesteps.
  :templatesteps is a list of templatestep maps. put-TemplateStep is called for every templatestep"
  [{:keys [templatesteps] :as template}]
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (let [template (-> template normalize-template-data (dissoc :id)),
          id (->> template               
               (jdbc/insert! t-conn, :template)
               first
               ; the project id (PRIMARY KEY AUTOINCREMENT) is an alias for the rowid in SQLite, hence it contains 
               rowid_keyword)]
      (doseq [step templatesteps]
        (create-templatestep t-conn, (assoc step :template id)))
      true)))


(defn update-template
  [{:keys [id], :as template-diff}, {:keys [added, modified, deleted] :as step-changes}]
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (jdbc/update! t-conn :template (normalize-template-data template-diff) ["id = ?" id])
    (doseq [added-step added]
      (create-templatestep t-conn, (assoc added-step :template id)))
    (doseq [modified-step modified]
      (update-templatestep t-conn, modified-step))
    (doseq [deleted-step deleted]
      (delete-templatestep t-conn, deleted-step)))
  true)


(defn delete-template
  "Deletes a template with its templatesteps"
  [template-id]
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (jdbc/delete! t-conn, :template ["id = ?" template-id])
    (jdbc/delete! t-conn, :templatestep ["template = ?" template-id]))
  true)


(defn delete-project
  "Deletes a project with its projectsteps"
  [project-id]
  (db-delete!
    (c/db-connection)
    :project ["id = ?" project-id])
  (db-delete!
    (c/db-connection)
    :projectstep ["project = ?" project-id]))

(defn read-project-steps
  ([project-id]
    (read-project-steps (c/db-connection), project-id))
  ([db-conn, project-id]
    (jdbc/query db-conn,
      ["SELECT projectstep.id, projectstep.type, projectstep.description, projectstep.freetext, projectstep.timestamp, projectstep.state, projectstep.advisor, projectstep.sequence
        FROM projectstep
        WHERE projectstep.project = ?" project-id])))


(defn step-exists
  ([project-id, step-id]
    (jdbc/with-db-transaction [t-conn (c/db-connection)]
      (step-exists t-conn, project-id, step-id)))
  ([db-conn, project-id, step-id]
    (jdbc/query db-conn,
      ["SELECT * FROM projectstep WHERE project = ? AND id = ?" project-id, step-id]
      :result-set-fn first)))


(defn read-customers
  "Get a list of all names and email adresses of the saved customers"
  ([]
    (read-customers (c/db-connection)))
  ([db-conn]
    (jdbc/query db-conn ["SELECT DISTINCT id, email, name FROM customer SORT"])))



(defn normalize-customer-attributes
  [customer]
  (-> customer
    (update-in [:name]  #(some-> % str/trim capitalize-name))
    (update-in [:email] #(some-> % str/trim str/lower-case))))

(defn normalize-customer-data
  [customer]
  (-> customer
    normalize-customer-attributes
    (select-keys [:name, :email])))


(def customer-key (juxt :name :email))


(defn maybe-add-customers
  "Adds the customers if needed and returns their customer id."
  [db-conn, customers]
  (let [existing-customers (read-customers db-conn),
        customer-id-map (zipmap (map customer-key existing-customers) (map :id existing-customers))]
    (reduce
      (fn [result, customer]
        (if-let [id (get customer-id-map (customer-key customer))]
          (conj result id)
          (let [id (->> customer
                     normalize-customer-data
                     (jdbc/insert! db-conn, :customer, )
                     first
                     rowid_keyword)]
            (conj result id))))
      []
      customers)))


(defn read-project-customers
  ([project-id]
    (read-project-customers (c/db-connection), project-id))
  ([t-conn, project-id]
    (jdbc/query t-conn,
      ["SELECT c.name, c.email, pc.sequence FROM customer AS c, projectcustomer AS pc WHERE pc.projectid = ? AND c.id = pc.customerid", project-id]
      :result-set-fn vec)))


(defn read-project-number
  ([project-id]
    (jdbc/with-db-transaction [t-conn (c/db-connection)]
      (read-project-number t-conn, project-id)))
  ([db-conn, project-id]
    (jdbc/query db-conn
      ["SELECT projectnumber FROM project WHERE project.id = ?" project-id]
      :result-set-fn #(some-> % first :projectnumber))))


(defn read-project
  "Get a project with projectsteps from database with given id"
  ([project-id]
    (read-project (c/db-connection), project-id))
  ([db-conn, project-id]
    (jdbc/with-db-transaction [t-conn db-conn]
      (when-let [project (jdbc/query t-conn
                           ; cant use a join here, because jdbc/query results in a flat vector and strugles with double keywords
                           ["SELECT * FROM project WHERE project.id = ?" project-id]
                           :result-set-fn first)]
        (let [users-to-notify (some-> (notified-users-of-project t-conn, (:id project)) sort (zipmap (repeat 1))),
              steps (vec (read-project-steps t-conn, project-id))]
          (cond-> (assoc project
                    :notifiedusers users-to-notify
                    :customers (read-project-customers t-conn, project-id))
            (seq steps) (assoc :projectsteps steps)))))))

(defn read-project-rowid
  "Rowid and id may differ. So use this function to read a project right after creation."
  ([row-id]
    (read-project-rowid (c/db-connection), row-id))
  ([db-conn, rowid]
    (->
      (jdbc/query db-conn ["SELECT id FROM project WHERE rowid=?" rowid] :result-set-fn #(-> % first :id))
      read-project)))

(defn read-project-by-tracking-nr
  "Get a project with projectsteps from database with given tracking-nr"
  [tracking-nr]
  (->
    (jdbc/query (c/db-connection) ["SELECT project.id FROM project WHERE project.trackingNr = ?" tracking-nr] :result-set-fn first)
    :id
    read-project))


(defn normalize-projectstep-data
  [projectstep]
  (select-keys projectstep [:id :type :description :freetext :timestamp :state :advisor :sequence :project]))


(defn normalize-project-data
  [project]
  (select-keys project
    [:id :trackingnr :description :dateofreceipt, 
     :advisor :orderform :flowcellnr :samplesheet :done :projectnumber :notifycustomer]))


(defn create-projectstep
  [t-conn, step]
  (jdbc/insert! t-conn, :projectstep, (-> step normalize-projectstep-data (dissoc :id))))


(defn update-projectstep
  ([step]
    (jdbc/with-db-transaction [t-conn (c/db-connection)]
      (update-projectstep t-conn, step)))
  ([t-conn, {:keys [id] :as step}]
    (jdbc/update! t-conn, :projectstep, (normalize-projectstep-data step), ["id = ?", id])))


(defn delete-projectstep
  [t-conn, {:keys [id] :as step}]
  (jdbc/delete! t-conn, :projectstep, ["id = ?", id]))



(defn read-project-customer-ids
  [t-conn, project-id]
  (jdbc/query t-conn, ["SELECT customerid FROM projectcustomer WHERE projectid = ?", project-id]
    :result-set-fn set
    :row-fn :customerid))


(defn add-customers-to-project
  [t-conn, project-id, customers-with-id-coll]
  (let [customers-with-id-coll (common/->vector customers-with-id-coll)
        n (count customers-with-id-coll),
        project-customer-id-set (read-project-customer-ids t-conn, project-id)]
    (loop [i 0]
      (when (< i n)
        (let [{:keys [id, sequence] :as customer} (nth customers-with-id-coll i)]
          (jdbc/insert! t-conn :projectcustomer {:projectid project-id, :customerid id, :sequence sequence}))
        (recur (unchecked-inc i))))))


(defn add-customers
  [project-id, customers]
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (let [customer-ids (maybe-add-customers t-conn, customers)]
      (add-customers-to-project t-conn, project-id, (mapv #(assoc %1 :id %2) customers customer-ids))
      true)))


(defn modify-customers
  [project-id, modified-customers]
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (let [customers (read-customers t-conn),
          customer-id-map (zipmap (map customer-key customers) (map :id customers))
          modified-customers (common/->vector modified-customers),
          n (count modified-customers)]
      (loop [i 0]
        (when (< i n)
          (let [customer (nth modified-customers i),
                customer-id (get customer-id-map (customer-key customer))]
            (jdbc/update! t-conn, :projectcustomer (select-keys customer [:sequence])
              ["projectid = ? AND customerid = ?", project-id, customer-id]))
          (recur (unchecked-inc i))))
      true)))


(defn remove-customers
  [project-id, removed-customers]
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (let [customers (read-customers t-conn),
          customer-id-map (zipmap (map customer-key customers) (map :id customers))
          removed-customers (common/->vector removed-customers),
          n (count removed-customers)]
      (loop [i 0]
        (when (< i n)
          (let [customer (nth removed-customers i),
                customer-id (get customer-id-map (customer-key customer))]
            (jdbc/delete! t-conn, :projectcustomer
              ["projectid = ? AND customerid = ?", project-id, customer-id]))
          (recur (unchecked-inc i))))
      true)))


(defn create-project
  "Inserts a project into the database and returns the project id. Must contain only:
  :id :trackingnr :description :dateofreceipt :customeremail :customername :advisor :orderform :flowcellnr :samplesheet :projectsteps :done"
  [{:keys [id, projectsteps, customers, notifiedusers] :as project}]
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (let [id (->> project
               normalize-project-data
               (jdbc/insert! t-conn, :project)
               first
               ; the project id (PRIMARY KEY AUTOINCREMENT) is an alias for the rowid in SQLite, hence it contains 
               rowid_keyword),
          customer-ids (maybe-add-customers t-conn, customers)]
      ; add customers to project
      (add-customers-to-project t-conn, id, (mapv #(hash-map :sequence %1 :id %2) (range) customer-ids))
      ; store user notifications
      (add-notified-user-for-project t-conn, id, notifiedusers)
      ; store project steps
      (doseq [step projectsteps]
        (create-projectstep t-conn, (assoc step :project id)))
      id)))


(defn update-project
  "Updates a project."
  [{:keys [id] :as project}, {:keys [added, modified, deleted] :as step-changes}]
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (jdbc/update! t-conn, :project, (normalize-project-data project) ["id = ?" id])
    ; user notifications are handled separately in traqbio.actions.project
    ; update project steps
    (doseq [added-step added]
      (create-projectstep t-conn, (assoc added-step :project id)))
    (doseq [modified-step modified]
      (update-projectstep t-conn, modified-step))
    (doseq [deleted-step deleted]
      (delete-projectstep t-conn, deleted-step)))
  true)


(defn insert-log
  "Inserts a log entry"
  [m]
  (try
    (db-insert! (c/db-connection) :actionlog m)
    (catch Throwable t
      (log/errorf "Exception when trying to add action log entry: %s" (pr-str m))
      (throw t))))

(defn read-log
  "Read entrys from actionlog"
  ([]
    (jdbc/query (c/db-connection)
      ["SELECT * FROM actionlog ORDER BY rowid DESC"]))
  ([limit]
    (jdbc/query (c/db-connection)
      ["SELECT * FROM actionlog ORDER BY rowid DESC LIMIT ?", limit]))
  ([offset, limit]
    (jdbc/query (c/db-connection)
      ["SELECT * FROM actionlog ORDER BY rowid DESC LIMIT ? OFFSET ?", limit, offset])))


(defn log-count
  "Determines the number of entries in actionlog"
  []
  (->
    (jdbc/query (c/db-connection)
      ["SELECT COUNT(*) AS logcount FROM actionlog"])
    first
    :logcount))


(defn read-finished-projects
  "Read all finished projects."
  []
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (let [finished-project-ids (jdbc/query t-conn ["SELECT id FROM project WHERE done = 1"] :row-fn :id)]
      (mapv #(read-project t-conn %) finished-project-ids))))


(defn read-current-project-ids
  ([]
    (jdbc/with-db-transaction [t-conn (c/db-connection)]
      (read-current-project-ids t-conn)))
  ([t-conn]
    (jdbc/query t-conn ["SELECT id FROM project WHERE done = 0"] :row-fn :id)))


(defn read-current-projects
  "Read current projects."
  []
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (let [project-ids (read-current-project-ids t-conn)]
      (mapv #(read-project t-conn %) project-ids))))


(defn next-default-projectnumber
  []
  (->> (jdbc/query (c/db-connection) ["SELECT projectnumber FROM project WHERE projectnumber LIKE \"P-%\""] :row-fn :projectnumber)
    (keep #(some->> % (re-find #"P-(\d+)") second Long/parseLong))
    (reduce max 0)
    inc
    (format "P-%s")))


(defn- create-uuid
  [db-conn, table, column]
  (loop []
    (let [id (.toUpperCase (.toString (java.util.UUID/randomUUID)))]
      (if (seq (jdbc/query db-conn, [(format "SELECT %1$s FROM %2$s WHERE %1$s = ?" (name column) (name table)), id]))
        (recur)
        id))))


(defn create-password-reset-request
  "Creates a password reset request and returns the request-id."
  [username]
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (let [request-id (create-uuid t-conn, :passwordreset, :resetrequestid),
          request {:username username, :resetrequestid request-id}]
      (if (seq (jdbc/query t-conn, ["SELECT username FROM passwordreset WHERE username = ?" username]))
        (jdbc/update! t-conn, :passwordreset, request ["username = ?" username])
        (jdbc/insert! t-conn, :passwordreset, request))
      request-id)))


(defn password-reset-data-by-id
  [reset-id]
  (first (jdbc/query (c/db-connection), ["SELECT * FROM passwordreset WHERE resetrequestid = ?" reset-id])))


(defn complete-password-reset
  [reset-id, username, new-password]
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (when (seq (jdbc/query t-conn, ["SELECT * FROM passwordreset WHERE resetrequestid = ?" reset-id]))
      (jdbc/update! t-conn, :user {:password (creds/hash-bcrypt new-password)} ["username = ?" username])
      (jdbc/delete! t-conn, :passwordreset ["resetrequestid = ?" reset-id])
      true)))