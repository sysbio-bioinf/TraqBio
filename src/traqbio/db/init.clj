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

(ns traqbio.db.init
  (:require
    [clojure.java.jdbc :as jdbc]
    [traqbio.config :as c]
    [clojure.java.io :as io]))



(defn create-table-if-needed
  [db-conn, table-set, table-name, column-spec-list]
  (when-not (contains? table-set table-name)
    (jdbc/db-do-commands
      db-conn
      (jdbc/create-table-ddl table-name, column-spec-list))))


(defn create-user-table
  [db-conn, table-set]
  (create-table-if-needed db-conn, table-set, :user
    [[:username "TEXT PRIMARY KEY"]
     [:password "TEXT"]
     [:fullname "TEXT"]
     [:role "TEXT"]
     [:email "TEXT"]]))


(defn create-template-table
  "template <-1--n-> template-step
  :description   = free text field to describe the project"
  [db-conn, table-set]
  (create-table-if-needed db-conn, table-set, :template
    [[:id "INTEGER PRIMARY KEY AUTOINCREMENT"]
     [:name "TEXT"]
     [:description "TEXT"]]))


(defn create-template-step-table
  "template-step <-n--1-> template
  :type        = Preparation, Sequencing, Alignment, ...
  :description = work to do in this step
  :freeText    = further notes
  :sequence    = order of the step in the template
  :template    = id of corresponding template (Foreign key)"
  [db-conn, table-set]
  (create-table-if-needed db-conn, table-set, :templatestep
    [[:id "INTEGER PRIMARY KEY AUTOINCREMENT"]
     [:type "TEXT"]
     [:description "TEXT"]
     [:sequence "INTEGER"]
     ; References template(id)
     [:template "INTEGER"]]))


(defn create-project-table
  "project <-1--n-> project-step
   project <-n--m-> customer
  :id
  :trackingnr    = ID for the public access and tracking of the project
  :description   = free text field to describe the project
  :dateofreceipt = timestamp for the acquisition of the project
  :additionalnotificationemails = additional e-mail addresses to notify
  :advisor       = employee who has accepted the order
  :orderform     = path to uploaded order form
  :flowcellnr    = number of the flowcell
  :samplesheet   = path to uploaded sample sheet
  :notifycustomer = notify customer on every step completion?"
  [db-conn, table-set]
  (create-table-if-needed db-conn, table-set, :project
    [[:id "INTEGER PRIMARY KEY AUTOINCREMENT"]
     [:template "INTEGER"]
     [:projectnumber "TEXT"]
     [:trackingnr "TEXT"]
     [:description "TEXT"]
     [:dateofreceipt "TEXT"]
     [:advisor "TEXT"]
     [:orderform "TEXT"]
     [:flowcellnr "TEXT"]
     [:samplesheet "TEXT"]
     [:notifycustomer "INTEGER"]
     [:done "INTEGER DEFAULT 0"]]))


(defn create-customer-table
  "project <-n--m-> customer
  :id
  :name
  :email"
  [db-conn, table-set]
  (create-table-if-needed db-conn, table-set, :customer
    [[:id "INTEGER PRIMARY KEY AUTOINCREMENT"]
     [:name "TEXT"]
     [:email "TEXT"]]))


(defn create-project-customer-table
  [db-conn, table-set]
  (create-table-if-needed db-conn, table-set, :projectcustomer
    [[:sequence "INTEGER"]
     [:projectid "INTEGER"]
     [:customerid "INTEGER"]
     ["PRIMARY KEY (projectid, customerid)"]]))


(defn create-project-step-table
  "project-step <-n--1-> project.
  :id
  :type        = Präparation, Sequencing, Alignment, ...
  :description = work to do in this step
  :freetext    = further notes
  :timestamp   = finished at date
  :state       = done (1) or undone (0)
  :advisor     = employee who has completed the step
  :sequence    = order of the step in the project
  :project     = id of corresponding project (Foreign key)"
  [db-conn, table-set]
  (create-table-if-needed db-conn, table-set, :projectstep
    [[:id "INTEGER PRIMARY KEY AUTOINCREMENT"]
     [:template "INTEGER"]
     [:type "TEXT"]
     [:description "TEXT"]
     [:freetext "Text"]
     [:timestamp "TEXT"]
     [:state "INTEGER"]                                     ; 0 = undone <--> 1 = done
     [:advisor "TEXT"]
     [:sequence "INTEGER"]
     ; References project(id)
     [:project "INTEGER"]]))


(defn create-action-log-table
  "Creates the table containing the logged actions performed by users."
  [db-conn, table-set]
  (create-table-if-needed db-conn, table-set, :actionlog
    [[:id "INTEGER PRIMARY KEY AUTOINCREMENT"]
     [:username "TEXT"]                                     ; References user(username)
     [:date "TEXT"]
     [:success "INTEGER"]
     [:meta "TEXT"]
     [:args "TEXT"]
     [:error "TEXT"]
     [:action "TEXT"]
     [:type "TEXT"]
     [:message "TEXT"]
     [:projectid "INTEGER"]]))


(defn create-text-module-table
  "Creates the table containing predefined text modules for free text of project steps.
   Adds a dummy textmodule so that table is not empty when read in by js for editTemplate and createTemplate."
  [db-conn, table-set]
  
  (do
  (create-table-if-needed db-conn, table-set, :textmodule
    [[:id "INTEGER PRIMARY KEY AUTOINCREMENT"] ;Identifies text module, unique
     [:template "INTEGER"] ; CHANGES: ADDED NEW COLUMN for assigning to correct template, same as in create-template-step-table
     [:step "INTEGER"]           ;Copy of :templatestepid column
     ;[:templatestepid "INTEGER"] ;ADDED: Default selected step in dropdown menu, no separate create-textmodule-templatestep-table necessary
     ;^both these should be INTEGER NOT NULL, remove null for testing
     [:name "TEXT"]
     [:text "TEXT"]])
  (jdbc/insert! (c/db-connection), :textmodule {:template 0 :step 0 :name " " :text " "})
    
  );end of do
  )

(comment ;Additional tables are not necessary
(defn create-textmodule-templatestep-table
  "Creates the table containing predefined text modules for free text of project steps."
  [db-conn, table-set]
  (create-table-if-needed db-conn, table-set, :textmoduletemplatestep
    [[:templatestepid "INTEGER NOT NULL"] ;Default selected step in dropdown menu
     [:textmoduleid "INTEGER NOT NULL"]   ;Identifies text module in database
     ["PRIMARY KEY (templatestepid, textmoduleid)"]]))
(defn create-textmodule-projectstep-table ;2. When a project is created from a template, add the text module association to the DB table `:textmoduleprojectstep`.
  "Creates the table containing predefined text modules for free text of project steps."
  [db-conn, table-set]
  (create-table-if-needed db-conn, table-set, :textmoduleprojectstep
    [[:projectstepid "INTEGER NOT NULL"] ;Identifies project
     [:textmoduleid "INTEGER NOT NULL"] ;Identifies text module in database
     ["PRIMARY KEY (projectstepid, textmoduleid)"]]))
);end of comment

(defn create-password-reset-table
  "Creates the table for password resets."
  [db-conn, table-set]
  (create-table-if-needed db-conn, table-set, :passwordreset
    [[:username "TEXT PRIMARY KEY"]                         ; References user(username)
     [:resetrequestid "TEXT"]]))


(defn create-user-notification-table
  "Creates the table for storing the specified TraqBio users to notify on project step completion events."
  [db-conn, table-set]
  (create-table-if-needed db-conn, table-set, :usernotification
    [[:username "TEXT"]
     [:projectid "INTEGER"]
     ["PRIMARY KEY (username, projectid)"]]))


(defn table-list
  "Returns a name list of the existing tables."
  [db-conn]
  (jdbc/with-db-metadata [metadata db-conn]
    (with-open [tables-resultset (.getTables metadata nil nil "%" nil)]
      (->> tables-resultset jdbc/result-set-seq (mapv (comp keyword :table_name))))))

;; NOTE: Added tables for v1.4.1
(defn create-dynamic-projectstep-table
  ; Write state of projectsteps *at the time point of project creation* to a separate table
  "Creates the table containing steps for projects. Steps can be altered in this table after the creation of a project
  independent of the state of the basis template that the project was created from."
  [db-conn, table-set]
  
  (do
  (create-table-if-needed db-conn, table-set, :dynamicprojectstep
[[:id "INTEGER PRIMARY KEY AUTOINCREMENT"]
     [:template "INTEGER"] ;NULL in db if not based on a template
     ; Need to write all 
     [:type "TEXT"] ; name of the step
     [:description "TEXT"]
     [:freetext "Text"]
     [:timestamp "TEXT"]
     [:state "INTEGER"]                                     ; 0 = undone <--> 1 = done
     [:advisor "TEXT"]
     [:sequence "INTEGER"] ; step number
     [:project "INTEGER"]] ; References project(id)
     )
  (jdbc/insert! (c/db-connection), :dynamicprojectstep {:project 0 :sequence 0 :type " " :description " " :state 0})
    
  );end of do
  )

  (defn create-dynamic-textmodules-table
  ;TODO: Needs ':template' nr that a project is based on
  ;*At the time point of project creation* copy all textmodules of the base template and write them to this new table
  "Creates the table containing textmodules for projects. Textmodules can be altered in this table after the creation of a project
  independent of the state of the basis template that the project was created from."
  [db-conn, table-set]

  (do
  (create-table-if-needed db-conn, table-set, :dynamictextmodules
    [[:id "INTEGER PRIMARY KEY AUTOINCREMENT"] ;Identifies element, unique
     [:projectid "INTEGER"] ; Should be linked to 'id' column of 'project' table
     [:template "INTEGER"]      ;number of base template that project was based on
     [:step "INTEGER"]         ;stepnr that the module belongs to
     [:name "TEXT"]        ;name of the module
     [:text "TEXT"]]       ;free text of the module
     )
  (jdbc/insert! (c/db-connection), :dynamictextmodules {:projectid 0 :step 0 :name " " :text " "})
    
  );end of do
  )

(defn create-missing-tables
  "Checks which tables exist and creates the tables that are missing.
  Only table names are checked."
  [db-conn]
  (let [table-set (set (table-list db-conn))]
    (create-user-table db-conn, table-set)
    (create-template-table db-conn, table-set)
    (create-template-step-table db-conn, table-set)
    (create-project-table db-conn, table-set)
    (create-customer-table db-conn, table-set)
    (create-project-customer-table db-conn, table-set)
    (create-project-step-table db-conn, table-set)
    (create-action-log-table db-conn, table-set)
    (create-password-reset-table db-conn, table-set)
    (create-user-notification-table db-conn, table-set)
    (create-text-module-table db-conn, table-set)
    ;(create-textmodule-templatestep-table db-conn, table-set)
    ;(create-textmodule-projectstep-table db-conn, table-set)

    ;(create-dynamic-projectstep-table db-conn, table-set)
    ;(create-dynamic-textmodules-table db-conn, table-set)
    ))



(defn create-database-if-needed
  "Creates the database file if it does not exist. Returns true if the database had to be created and false otherwise."
  [db-filename]
  (if (.exists (io/file db-filename))
    false
    (let [db-conn (c/pool (c/database-config db-filename))]
      (create-missing-tables db-conn)
      true)))
