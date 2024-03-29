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

(ns traqbio.db.crud
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as log]
    [cemerick.friend.credentials :as creds]
    [traqbio.config :as c]
    [traqbio.common :as common]
    [clojure.pprint :as pp]))



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
    (c/db-connection)
    ["SELECT * FROM user WHERE username = ?" user-name]
    {:result-set-fn fn}))

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
    (mapv #(first (jdbc/query t-conn ["SELECT username, fullname, email FROM user WHERE username = ?" %])) user-list)))

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
     ;(apply db-insert! db-conn, :usernotification, (mapv #(hash-map :projectid project-id, :username %) usernames)))
     ;Handle analogously to removal of notified users, loop over all changes -> can add >2 users at the same time now
     (jdbc/with-db-transaction [t-conn db-conn]
       (doseq [user usernames]
         (jdbc/insert! t-conn, :usernotification, {:username user :projectid project-id}))))
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
    ["SELECT * FROM template"])) ;NOTE: Called by routes.clj template-api-routes, provides template in { for template in templates }, templates comes from templates.clj template-list function

(defn- read-template-steps
  [template-id]
  (jdbc/query
    (c/db-connection)
    ["SELECT templatestep.id, templatestep.type, templatestep.description, templatestep.sequence
      FROM templatestep
      WHERE templatestep.template = ?" template-id]))

;NOTE: NEED this version of read-textmodules for template-edit html site to render! it is called later by read-template
;(comment
(defn read-textmodules
  [template-step-id-list template-id]
  (into []
    (comp
      (keep
        (fn [step-id]
          (some->> (jdbc/query
                     (c/db-connection)
                      ;["SELECT tm.id, tm.name, tm.text
                      ; FROM textmoduletemplatestep AS ms, textmodule AS tm
                      ; WHERE ms.templatestepid = ? AND ms.textmoduleid = tm.id" ;CHANGE, added SELECT tm.template...MATCHING OF ID's is checked here
                      ;step-id])
                     ["SELECT * FROM textmodule AS tm
                        WHERE tm.step = ? AND tm.template = ?";Old version? Read only entries where ... AND tm.template = tempid argument given?
                       step-id template-id]);end of query
            not-empty
            (mapv #(assoc % :step step-id)))));end of keep fn
      cat);end of comp
    template-step-id-list))
;);end comment

;NOTE: Alternative simpler versions
(defn read-text-modules
  "Get all textmodules which belong to the given project."
  [project-id]
  (jdbc/query
    (c/db-connection)
    ["SELECT * FROM textmodule
    INNER JOIN project ON textmodule.template=project.template
    WHERE project.id = ?" project-id])) ;Used in projectedit, needs to be coupled with selmer templates for showing step specific dropdowns

;TODO 17.1.22:
(comment
(defn read-dynamic-textmodules
"Get all textmodules which belong to the given project, given their state as it was when the project was created."
))

(defn get-templatenr-from-projid
  [project-id]
  (jdbc/query
   (c/db-connection)
   ["SELECT template FROM project
    WHERE project.id = ?" project-id])
  )

(defn reorder-module-ids
  "Rewrites indices column of textmodule table in order to close gaps, content remains the same."
  [all-textmodules] ;pass the result of calling read-all-text-modules -> receives multiple maps
      ;(jdbc/delete! (c/db-connection) :textmodule) ;reset :textmodule db
      ;(jdbc/insert-multi! (c/db-connection) :textmodule all-textmodules) ;fill db again with reordered id numbers
  ;
  ;TODO: Loop assoc-in over all maps in all-textmodules, first 1 is index of user, second is counter
  ;loop goes from counter = 0 to number of maps
    (assoc-in [all-textmodules] [1 :id] 1)
    
  )


(defn read-all-text-modules
  "Get the entire textmodule database"
  []
  (jdbc/query
   (c/db-connection)
    ["SELECT * FROM textmodule"]))

(comment ;Used by read-template function. Works to show template-specific modules, but step is taken as templatestep.id
 (defn read-modules-by-template
   [tmplId]
   (jdbc/query
     (c/db-connection)
     ["SELECT * FROM textmodule AS tm
    WHERE tm.template = ?" tmplId])))


(defn read-modules-by-template
  [tmplId]
  (jdbc/query
    (c/db-connection)
    ["SELECT textmodule.id, textmodule.template, textmodule.step, textmodule.name, textmodule.text FROM textmodule
    WHERE textmodule.template = ?" tmplId]))
    


(defn read-projectsteps
  "Get all projectsteps"
  []
  (jdbc/query
    (c/db-connection)
    ["SELECT * FROM projectstep"]))


(defn read-template
  "Returns a template-map with joined templatesteps"
  [template-id]
  (when-let [template (jdbc/query
                        ; cant use a join here, because jdbc/query results in a flat vector and struggles with double keywords
                        (c/db-connection)
                        ["SELECT template.id, template.name, template.description
                          FROM template
                          WHERE template.id = ?" template-id]
                        {:result-set-fn first})]
    (let [template-steps (vec (sort-by :sequence (read-template-steps template-id)))]
      (assoc template
        :templatesteps template-steps
        ;:textmodules (read-textmodules (mapv :id template-steps) template-id)
        :textmodules (read-modules-by-template template-id)))))
        


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
    (jdbc/query (c/db-connection)
      ["SELECT id FROM template WHERE rowid=?" rowid]
      {:result-set-fn #(-> % first :id)})
    read-template))


(defn normalize-templatestep-data
  [templatestep]
  (select-keys templatestep [:id :type :description :sequence :template]))

;NOTE: New function inserted here. Analogous to normalize-templatestep-data.
(defn normalize-textmodule-data
  [module]
  (select-keys module [:id :template :step :name :text])) ;NOTE: Removed :templatestepid key


(defn create-templatestep
  "Inserts or updates a templatestep.
  step must contain only :id :type :description :sequence :template
  See create-template-step-table"
  [t-conn, step]
  (-> (jdbc/insert! t-conn, :templatestep, (-> step normalize-templatestep-data (dissoc :id)))
    first
    rowid_keyword))


(defn update-templatestep
  [t-conn, {:keys [id] :as step}]
  (jdbc/update! t-conn, :templatestep, (normalize-templatestep-data step), ["id = ?", id]))

;NOTE: New function inserted here.
(defn update-textmodule
  [t-conn, {:keys [id] :as module}]
  (jdbc/update! t-conn, :textmodule, (normalize-textmodule-data module), ["id = ?", id]))

(defn delete-templatestep
  [t-conn, {:keys [id]}]
  (jdbc/delete! t-conn, :templatestep, ["id = ?", id]))

;NOTE: New function inserted here
(defn delete-textmodule
  [t-conn, {:keys [id]}]
  (jdbc/delete! t-conn, :textmodule, ["id = ?", id]))

(defn create-template-steps
  [t-conn, template-id, template-steps]
  (persistent!
    (reduce
      (fn [step-id->db-id, {:keys [id] :as step}]
        (let [db-id (create-templatestep t-conn, (assoc step :template template-id))]
          (assoc! step-id->db-id id db-id)))
      (transient {})
      template-steps)))

(defn delete-all-textmodules-by-template [t-conn, template]
  ;Use existing t-conn from update-template to jdbc/update! the :textmodule database
  ;Access textmodule database and delete all rows in which :template key matches provided template argument
  (jdbc/delete! t-conn :textmodule ["template = ?" template]))

(defn write-all-textmodules-from-templateData [t-conn, textmodules]
  (jdbc/insert-multi! t-conn :textmodule textmodules))

;Correctly increments id column, rowid doesn't necessarily match id after module deletions
(defn create-textmodule
  [t-conn, text-module]
  (-> (jdbc/insert! t-conn, :textmodule, (dissoc text-module :id, :step))
    first
    rowid_keyword))
        ;Analogous to creation of create-templatestep:
  ;  [t-conn, step]
  ;  (-> (jdbc/insert! t-conn, :templatestep, (-> step normalize-templatestep-data (dissoc :id)))
  ;    first
  ;    rowid_keyword))


(defn create-template-module-steps
  [t-conn, text-modules]
  (persistent!
    (reduce
      (fn [db-id->step-id, {:keys [step] :as module}]
        (if (>= step 0)
          (let [db-id (create-textmodule t-conn, module)]
            (assoc! db-id->step-id db-id step))
          db-id->step-id))
      (transient {})
      text-modules)))


(defn assign-text-modules-to-template-steps
  [t-conn, module-db-id->step-db-id]
  (jdbc/insert-multi! t-conn, :textmoduletemplatestep
    (mapv
      (fn [[module-db-id, step-db-id]]
        {:templatestepid step-db-id, :textmoduleid module-db-id})
      module-db-id->step-db-id)))


(defn normalize-template-data
  [template]
  (select-keys template [:id :name :description]))

(comment ;Doesn't write textmodules to db
(defn create-template
  "Inserts or updates a template.
  template must only contain :id :name :description :advisor :customeremail :customername :flowcellnr :templatesteps.
  :templatesteps is a list of templatestep maps. put-TemplateStep is called for every templatestep"
  [{:keys [templatesteps, textmodules] :as template}]
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (let [template (-> template normalize-template-data (dissoc :id)),
          template-id (->> template
                        (jdbc/insert! t-conn, :template)
                        first
                        ; the project id (PRIMARY KEY AUTOINCREMENT) is an alias for the rowid in SQLite, hence it contains
                        rowid_keyword)
          ; insert template steps
          step-id->db-id (create-template-steps t-conn, template-id, templatesteps)
          ; insert text modules
          module-db-id->step-id (create-template-module-steps t-conn, textmodules)
          module-db-id->step-db-id (common/replace-map-vals module-db-id->step-id step-id->db-id)]
      (assign-text-modules-to-template-steps t-conn, module-db-id->step-db-id)
      true)))
)

;NOTE: Also writes modules which were added in createTemplate to db with template nr matching the ever increasing rowid_keyword
(defn create-template
  "Inserts or updates a template.
  template must only contain :id :name :description :advisor :customeremail :customername :flowcellnr :templatesteps.
  :templatesteps is a list of templatestep maps. put-TemplateStep is called for every templatestep"
  [templateData {:keys [templatesteps, textmodules] :as template}]
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (let [template (-> template normalize-template-data (dissoc :id))
          template-id (->> template
                           (jdbc/insert! t-conn, :template)
                           first
                        ; the project id (PRIMARY KEY AUTOINCREMENT) is an alias for the rowid in SQLite, hence it contains
                           rowid_keyword)
          ; insert template steps
          step-id->db-id (create-template-steps t-conn, template-id, templatesteps)
          ; insert text modules - get templateData from actions/templates.clj which gets it from js -> modify template number to match template-id/rowid
          templateData (assoc templateData :id template-id)
          templateDataTMs (mapv #(assoc % :template template-id) (get templateData :textmodules))
          templateData (assoc templateData :textmodules templateDataTMs) ;both id and template in tms are correctly matching template-id now!
          write-tm-batch (write-all-textmodules-from-templateData t-conn, (get templateData :textmodules))
          ];end of let
      ;Works as intended, but js also needs max already present textmodule id
      true)))



(comment ;NOTE: old, works, but does not change textmodule table in db
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

   true))
;end comment

(comment ;NOTE: This function actually made changes to textmodule db, but wrote only first two columns
 (defn update-template
   [{:keys [id], :as template-diff},
    {:keys [added, modified, deleted] :as step-changes}, {:keys [addedmod, modifiedmod, deletedmod] :as module-changes}] ;new :keys for modules added
   (do
    (jdbc/with-db-transaction [t-conn (c/db-connection)]
      (jdbc/update! t-conn :template (normalize-template-data template-diff) ["id = ?" id])
      (doseq [added-step added]
        (create-templatestep t-conn, (assoc added-step :template id)))
      (doseq [modified-step modified]
        (update-templatestep t-conn, modified-step))
      (doseq [deleted-step deleted]
        (delete-templatestep t-conn, deleted-step))
;     Added more doseqs for create-module, update-module and delete-module -> change textmodule table in database
     (doseq [added-module addedmod]
       (create-textmodule t-conn, (assoc added-module :template id)))
     (doseq [modified-module modifiedmod]
       (update-textmodule t-conn, modified-module))
     (doseq [deleted-module deletedmod]
       (delete-textmodule t-conn, deleted-module)))
     ;end of db-transaction
    true)))
   ;end comment

(defn reorder-module-ids-db
  [t-conn]
  
  
  ;(jdbc/update!
  ; t-conn :textmodule {:step 1} ["ceil(step) = step"];(range 1 13)
   ;ALTER TABLE textmodule AUTO_INCREMENT = 1 ;To reset autoincrement counter to 1
   ;TEST: ["UPDATE textmodule SET id INTEGER PRIMARY KEY AUTOINCREMENT"]
   ;["SET @row := 0; UPDATE textmodule SET id = (@row := @row + 1)"]
  ; )
  
  (jdbc/execute! t-conn ;["UPDATE textmodule SET step INTEGER PRIMARY KEY AUTOINCREMENT"]
                 ["ALTER TABLE textmodule AUTO_INCREMENT = 300"]
                 )
  
  ;drop id column from textmodule table, re-add it as :id "INTEGER PRIMARY KEY AUTOINCREMENT"?
      ;ALTER TABLE `textmodule `DROP `id `;
      ;ALTER TABLE `textmodule `AUTO_INCREMENT = 1;
      ;ALTER TABLE `textmodule `ADD `id `int UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;
  
  ;CREATE SEQUENCE seq_id
  ;MINVALUE 1
  ;START WITH 1
  ;INCREMENT BY 1
  ;CACHE 10; 
  ;INSERT INTO textmodule (id)
  ;VALUES (seq_id.nextval); 
  
  ;"INTEGER PRIMARY KEY AUTOINCREMENT"  ; id is already made unique in table definition, need to only reset autoincrement?
  
  )

(defn update-template
  [{:keys [id], :as template-diff},
   {:keys [added, modified, deleted] :as step-changes},
   textmodules] ;textmodules contains entire templateData as given in js, including :textmodules table

  (do
   (jdbc/with-db-transaction [t-conn (c/db-connection)]
     (jdbc/update! t-conn :template (normalize-template-data template-diff) ["id = ?" id])
     (doseq [added-step added]
       (create-templatestep t-conn, (assoc added-step :template id)))
     (doseq [modified-step modified]
       (update-templatestep t-conn, modified-step))
     (doseq [deleted-step deleted]
       (delete-templatestep t-conn, deleted-step))
    
     (delete-all-textmodules-by-template t-conn, (:id textmodules))
     (write-all-textmodules-from-templateData t-conn, (get textmodules :textmodules))
    
     ;(reorder-module-ids-db t-conn)
      );end of db-transaction
   true))
  



(defn delete-template
  "Deletes a template with its templatesteps"
  [template-id]
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (jdbc/delete! t-conn, :template ["id = ?" template-id])
    (jdbc/delete! t-conn, :templatestep ["template = ?" template-id])
    (delete-all-textmodules-by-template t-conn, template-id))
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
     ["SELECT projectstep.id, projectstep.template, projectstep.type, projectstep.description, projectstep.freetext, projectstep.timestamp, projectstep.state, projectstep.advisor, projectstep.sequence
        FROM projectstep
        WHERE projectstep.project = ?" project-id])))


(defn step-exists
  ([project-id, step-id]
   (jdbc/with-db-transaction [t-conn (c/db-connection)]
     (step-exists t-conn, project-id, step-id)))
  ([db-conn, project-id, step-id]
   (jdbc/query db-conn,
     ["SELECT * FROM projectstep WHERE project = ? AND id = ?" project-id, step-id]
     {:result-set-fn first})))


(defn read-customers
  "Get a list of all names and email adresses of the saved customers"
  ([]
   (read-customers (c/db-connection)))
  ([db-conn]
   (jdbc/query db-conn ["SELECT DISTINCT id, email, name FROM customer SORT"])))



(defn normalize-customer-attributes
  [customer]
  (-> customer
    (update-in [:name] #(some-> % str/trim capitalize-name))
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
                     (jdbc/insert! db-conn, :customer,)
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
     {:result-set-fn vec})))


(defn read-project-number
  ([project-id]
   (jdbc/with-db-transaction [t-conn (c/db-connection)]
     (read-project-number t-conn, project-id)))
  ([db-conn, project-id]
   (jdbc/query db-conn
     ["SELECT projectnumber FROM project WHERE project.id = ?" project-id]
     {:result-set-fn #(some-> % first :projectnumber)})))


(defn read-project
  "Get a project with projectsteps from database with given id"
  ([project-id]
   (read-project (c/db-connection), project-id))
  ([db-conn, project-id]
   (jdbc/with-db-transaction [t-conn db-conn]
     (when-let [project (jdbc/query t-conn
                          ; cant use a join here, because jdbc/query results in a flat vector and struggles with double keywords
                          ["SELECT * FROM project WHERE project.id = ?" project-id]
                          {:result-set-fn first})]
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
     (jdbc/query db-conn ["SELECT id FROM project WHERE rowid=?" rowid] {:result-set-fn #(-> % first :id)})
     read-project)))

(defn read-project-by-tracking-nr
  "Get a project with projectsteps from database with given tracking-nr"
  [tracking-nr]
  (->
    (jdbc/query (c/db-connection) ["SELECT project.id FROM project WHERE project.trackingNr = ?" tracking-nr] {:result-set-fn first})
    :id
    read-project))


(defn normalize-projectstep-data
  [projectstep]
  (select-keys projectstep [:id :template :type :description :freetext :timestamp :state :advisor :sequence :project]))


(defn normalize-project-data
  [project]
  (select-keys project
    [:id :template :trackingnr :description :dateofreceipt,
     :advisor :orderform :flowcellnr :samplesheet :done :projectnumber :notifycustomer]))


(defn create-projectstep
  [t-conn, step]
  ;(do
  (jdbc/insert! t-conn, :projectstep, (-> step normalize-projectstep-data (dissoc :id))));end of insert
  ;);end do


(defn update-projectstep
  ([step]
   (jdbc/with-db-transaction [t-conn (c/db-connection)]
     (update-projectstep t-conn, step)))
  ([t-conn, {:keys [id] :as step}]
   (jdbc/update! t-conn, :projectstep, (normalize-projectstep-data step), ["id = ?", id])))


(defn delete-projectstep
  [t-conn, {:keys [id] :as step}]
  (jdbc/delete! t-conn, :projectstep, ["id = ?", id]))


;TODO 17.1.22: Writing to new dynamic textmodules table upon project-creation button click (todo implement function call upon button click)
(comment
(defn update-dynamictextmodules
  ;TODO: Get the templatenr from dropdown selection in createProject js 


  ;read all textmodules from :textmodules that match this template nr == (jdbc/query)
  ;Save all those textmodules to new database
  (jdbc/insert-multi! db-spec :dynamictextmodules (jdbc/query db-spec ["SELECT * FROM textmodules WHERE template = ?", templatenr]))

   )
);end comment


(defn read-project-customer-ids
  [t-conn, project-id]
  (jdbc/query t-conn, ["SELECT customerid FROM projectcustomer WHERE projectid = ?", project-id]
    {:result-set-fn set
     :row-fn :customerid}))


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


(defn create-project ;Added :template key to project table
  "Inserts a project into the database and returns the project id. Must contain only:
  :id :template :trackingnr :description :dateofreceipt :customeremail :customername :advisor :orderform :flowcellnr :samplesheet :projectsteps :done"
  [{:keys [id, template, projectsteps, customers, notifiedusers] :as project}]
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
        ; Write template that project is based on to database :project as well as :projectsteps tables as well
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
    (let [finished-project-ids (jdbc/query t-conn ["SELECT id FROM project WHERE done = 1"] {:row-fn :id})]
      (mapv #(read-project t-conn %) finished-project-ids))))


(defn read-current-project-ids
  ([]
   (jdbc/with-db-transaction [t-conn (c/db-connection)]
     (read-current-project-ids t-conn)))
  ([t-conn]
   (jdbc/query t-conn ["SELECT id FROM project WHERE done = 0"] {:row-fn :id})))


(defn read-current-projects
  "Read current projects."
  []
  (jdbc/with-db-transaction [t-conn (c/db-connection)]
    (let [project-ids (read-current-project-ids t-conn)]
      (mapv #(read-project t-conn %) project-ids))))


(defn next-default-projectnumber
  []
  (->> (jdbc/query (c/db-connection) ["SELECT projectnumber FROM project WHERE projectnumber LIKE \"P-%\""] {:row-fn :projectnumber})
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
