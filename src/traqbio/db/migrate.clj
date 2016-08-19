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

(ns traqbio.db.migrate
  (:require
    [clojure.java.io :as io]
    [clojure.java.jdbc :as jdbc]
    [traqbio.config :as c]
    [traqbio.db.init :as init]
    [traqbio.db.crud :as crud]))


(defn table-set
  [db-conn]
  (set
    (jdbc/query db-conn
      ["SELECT name FROM sqlite_master WHERE type='table'"] :row-fn (comp keyword :name))))


(defn table-columns
  [db-conn, table]
  (jdbc/query db-conn [(format "PRAGMA table_info(%s)" (name table))]
    :row-fn (comp keyword :name),
    :result-set-fn vec))


(defn db-data
  [db-conn]
  (let [tables (table-set db-conn)]
    (persistent!
      (reduce
        (fn [table-data-map, table]
          (cond-> table-data-map
            (contains? tables table)
            (assoc! table (vec (jdbc/query db-conn [(format "SELECT * FROM %s" (name table))])))))
        (transient {})
        [:project, :projectstep :template :templatestep :customer :projectcustomer :user :usernotification :passwordreset :actionlog]))))


(defn db-connection
  [db-filename]
  (c/pool (c/database-config db-filename)))


(defn export-data
  [db-filename, export-filename]
  (let [data (db-data (db-connection db-filename))]
    (with-open [w (io/writer export-filename)]
      (binding [*out* w]
        (prn data)))))


(defn export-templates
  [db-filename, export-filename]
  (let [templates-vec (do
                        (c/update-db-name db-filename)
                        (reduce
                          (fn [result-vec, {:keys [id]}]
                            (conj result-vec
                              (-> id
                                crud/read-template
                                (select-keys [:name, :description, :templatesteps])
                                (update-in [:templatesteps] (partial mapv #(select-keys % [:type, :description]))))))
                          []
                          (crud/read-templates)))]
    (with-open [w (io/writer export-filename)]
      (binding [*out* w]
        (prn templates-vec)))))


(defn insert-table-data
  [db-conn, table-data-map]
  (reduce-kv
    (fn [_, table, rows]
      (let [table-cols (table-columns db-conn, table)]
        (reduce
          (fn [_, row-data]
            (jdbc/insert! db-conn, table, (select-keys row-data table-cols))
            nil)
          nil
          rows)))
    nil
    table-data-map))



(defn customer-in-project?
  [table-data-map]
  (let [imported-project-columns (->> table-data-map :project (mapcat keys) set)]
    (contains? imported-project-columns :customername)))


(defn customer-project-id-map
  [table-data-map]
  (reduce
    (fn [m, {:keys [customername, customeremail, id]}]
      (let [customer (crud/normalize-customer-data {:name customername :email customeremail})]
        (update-in m [customer] (fnil conj []) id)))
    {}
    (:project table-data-map)))


(defn create-customers-and-mapping
  [db-conn, customer-project-map]
  (let [customers (keys customer-project-map),
        customer-id-map (zipmap customers (crud/maybe-add-customers db-conn, customers))]
    (reduce-kv
      (fn [_, customer, customer-id]
        (let [projects (get customer-project-map customer)]
          (doseq [project-id projects]
            (jdbc/insert! db-conn :projectcustomer {:projectid project-id, :customerid customer-id, :sequence 0})))
        nil)
      nil
      customer-id-map)))


(defn create-customer-table-if-needed
  [db-conn, table-data-map]
  (when (customer-in-project? table-data-map)
    (let [customer-project-map (customer-project-id-map table-data-map)]
      (jdbc/with-db-transaction [t-conn db-conn]
        (create-customers-and-mapping t-conn, customer-project-map)))))



(defn import-data
  [db-filename, import-filename]
  (let [table-data-map (read (java.io.PushbackReader. (io/reader import-filename)))]
    (init/create-database-if-needed db-filename)
    (let [db-conn (db-connection db-filename)]
      (insert-table-data db-conn, table-data-map)
      (create-customer-table-if-needed db-conn, table-data-map)
      nil)))