

(defn get-version
  []
  (let [version-fn (try
                     (load-file "src/traqbio/version.clj")
                     (catch java.io.FileNotFoundException e
                       ; workaround for CCW (version number is not needed anyway)
                       (constantly "0.0.0-REPL-DEV")))]
    (version-fn)))

(def version (get-version))

(defproject traqbio version
  :description "Track laboratory projects"
  :main traqbio.core
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [compojure "1.6.1"]
                 [com.cemerick/friend "0.2.3"]
                 [ring/ring-core "1.8.1"]
                 [ring/ring-jetty-adapter "1.8.1"]
                 [ring/ring-json "0.5.0"]
                 [metosin/ring-http-response "0.9.1"]       ; Exception handling in responses
                 [ring/ring-headers "0.3.0"]                ; replacing :remote-addr with origin address in proxy scenario
                 [org.clojure/java.jdbc "0.7.11"]           ; JDBC Binding
                 [com.mchange/c3p0 "0.9.5.5"]               ; connection pool
                 [org.xerial/sqlite-jdbc "3.31.1"]          ; SQLite
                 [selmer "1.12.23"]                         ; Templating
                 [org.clojure/tools.cli "1.0.194"]
                 [com.draines/postal "2.0.3"]
                 [org.clojure/tools.logging "1.0.0"]        ; logging, e.g. for fail2ban usage
                 [org.slf4j/slf4j-api "1.7.30"]
                 [org.slf4j/slf4j-log4j12 "1.7.30"]]
  :jar-name "traqbio-lib-%s.jar"
  :uberjar-name "traqbio-%s.jar"
  :profiles {:uberjar {:aot :all},
             :dev
             {:dependencies
              [[expectations "2.1.1"]]
              :plugins
              [[lein-autoexpect "1.4.2"]]}})
