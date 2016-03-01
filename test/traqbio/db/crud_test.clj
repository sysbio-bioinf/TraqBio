(ns traqbio.db.crud-test
  (:use expectations)
  (:require
    [traqbio.config :as cfg]
    [traqbio.core :as core]
    [traqbio.db.crud :as crud]))


(def ^:const test-config
  {:log-level :off
   :log-file  "traqbio-test.log"
   :data-base-name "traqbio-test.db"
   :admin-shutdown? false
   :upload-path "test-uploads/"
   :server-config {:port 42235
                   :host "localhost"
                   :join? false
                   :ssl? false
                   :forwarded? false
                   :proxy-url nil},
   
   :mail-config {:send-mail? false}})


(defn setup-traqbio-test-instance
  "loads test data"
  {:expectations-options :before-run}
  []
  (cfg/update-config test-config)
  (let [data-base-name (:data-base-name test-config)]
    (core/create-database-if-needed data-base-name, "johndoe", "secret")
    (cfg/update-db-name data-base-name)))


(defn parallel-database-writes
  [n]
  (let [project-data
        {:description "Quantitative Phosphoproteomics",
         :flowcellnr nil,
         :advisor "Jane Doe",
         :name "Quantitative Phophorylation analysis",
         :customername "Max Mustermann",
         :customeremail "max.mustermann@example.com"
         :templatesteps
         [{:advisor "John Doe", :freetext nil, :sequence 1, :description "Announcement", :type "Job request", :state 0}
          {:advisor "Jane Doe", :freetext nil, :sequence 2, :description "Arrival", :type "Sample Delivery", :state 0}
          {:advisor "John Doe", :freetext nil, :sequence 3, :description "Processed", :type "Sample Preparation", :state 0}]},
        signal (promise),
        futures (vec
                  (for [_ (range n)]
                    (future
                      (deref signal)
                      (crud/create-project project-data)
                      true)))]
    ; start synchronized futures
    (deliver signal true)
    ; unwrap potentially thrown exception
    (try
      (every? deref futures)
      (catch Throwable t
        (throw (or (.getCause t) t))))))


;; Test for Bug #66: SQLException [SQLITE_BUSY] The database file is locked (database is locked)
(expect true (parallel-database-writes 100))


(defn parallel-database-updates
  [n]
  (let [project-data
        {:id 1
         :description "Quantitative Phosphoproteomics",
         :flowcellnr nil,
         :advisor "Jane Doe",
         :name "Quantitative Phophorylation analysis",
         :customername "Max Mustermann",
         :customeremail "max.mustermann@example.com"         
         :templatesteps
         [{:advisor "John Doe", :freetext nil, :sequence 1, :description "Announcement", :type "Job request", :state 0}
          {:advisor "Jane Doe", :freetext nil, :sequence 2, :description "Arrival", :type "Sample Delivery", :state 0}
          {:advisor "John Doe", :freetext nil, :sequence 3, :description "Processed", :type "Sample Preparation", :state 0}]},
        signal (promise),
        futures (vec
                  (for [_ (range n)]
                    (future
                      (deref signal)
                      (crud/update-project (update-in project-data [:notifiedusers]
                                             (fn [m]
                                               (assoc m
                                                 :abc (rand-nth [0 1])
                                                 :def (rand-nth [0 1])
                                                 :ghi (rand-nth [0 1])
                                                 :jkl (rand-nth [0 1])
                                                 :mno (rand-nth [0 1])))))
                      true)))]
    ; start synchronized futures
    (deliver signal true)
    ; unwrap potentially thrown exception
    (try
      (every? deref futures)
      (catch Throwable t
        (throw (or (.getCause t) t))))))

(expect true (parallel-database-updates 100))