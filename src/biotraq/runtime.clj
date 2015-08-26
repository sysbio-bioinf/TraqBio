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

(ns biotraq.runtime
  (:require
    [clojure.string :as str]
    [clojure.stacktrace :refer [print-cause-trace]]
    [ring.adapter.jetty :as jetty]
    [clojure.tools.logging :as log]
    [biotraq.config :as c])
  (:import
    (org.eclipse.jetty.server Server AbstractConnector)
    org.eclipse.jetty.server.ssl.SslSelectChannelConnector
    org.apache.log4j.PropertyConfigurator
    java.util.Properties))


; variables instead of atom since the values are only changed at startup and shutdown
(def ^:private running-server nil)
(def ^:private keep-running true)


(defn keep-running?
  []
  keep-running)


(defn use-only-tls
  "For security reasons use only TLSv1.1 and TLSv1.2."
  [^Server jetty]
  (let [protocols (into-array String ["TLSv1.1" "TLSv1.2"])]
    (doseq [^SslSelectChannelConnector con (->> jetty
                                             .getConnectors
                                             (filter #(instance? SslSelectChannelConnector %)))]
      (.setIncludeProtocols (.getSslContextFactory con) protocols))))


(defn use-forwarding
  "Configure jetty to be used via forwarding."
  [^Server jetty]
  (log/infof "Configuring jetty to be used via forwarding.")
  (doseq [^AbstractConnector con (.getConnectors jetty)]
    (.setForwarded con true)))

(defn configurator
  [& fns]
  (let [fns (remove nil? fns)]
    (fn [jetty]
      (doseq [f fns]
        (f jetty)))))


(defn- server-url
  [host, port, ssl? server-root]
  (let [proto (if ssl? "https" "http")]
    (if (str/blank? server-root)
      (format "%s://%s:%s"    proto host port)
      (format "%s://%s:%s/%s" proto host port server-root))))

(defn start-server
  [app]
  (when-not running-server
    (alter-var-root #'keep-running (constantly true))
    (let [{:keys [ssl?, forwarded?] :as config} (c/server-config),
          server (jetty/run-jetty
                   app
                   (cond-> config
                     (or ssl? forwarded?) (assoc :configurator (configurator (when ssl? use-only-tls), (when forwarded? use-forwarding)))
                     (not ssl?) (dissoc :ssl-port :key-password :keystore)))]
      (alter-var-root #'running-server (constantly server))
      (let [{:keys [host, port, ssl-port, ssl?, server-root]} (c/server-config)]
        (println "BioTraq started - Server listening on:")
        (println (server-url host, port, false, server-root))
        (when ssl?
          (println (server-url host, ssl-port, true, server-root))))
      server)))


(def ^:private pending-requests (atom 0))


(defn wait-for-completed-requests
  [timeout]
  (loop []
    (when (pos? @pending-requests)
      (Thread/sleep timeout)
      (recur))))


(defn wrap-shutdown
  [shutdown-routes, handler]
  (fn [request]
    (if (keep-running?)
      ; execute handler and keep track of the number of pending operations
      (try
        (swap! pending-requests inc)
        (handler request)
        (finally
          (swap! pending-requests dec)))
      ; display shutdown notice
      (shutdown-routes request))))


(defn stop-server []
  (try
    (when running-server
      (binding [log/*force* :direct]
        (alter-var-root #'keep-running (constantly false))
        (log/info "Server shutdown initiated.")
        (future        
          (try
            (log/info "Waiting for requests to complete ...")
            ; wait for pending requests to complete
            (wait-for-completed-requests 250)
            ; sleep 1 second to let pending requests be served (since pending request are counted as completed in the middleware)
            (Thread/sleep 1000)
            (shutdown-agents)            
            (log/info "Server shutdown finished.")
            ; stop jetty
            (let [server running-server]
              (alter-var-root #'running-server (constantly nil))
              (.stop ^Server server))
            ; after stopping the jetty server the program will exit
            (catch Throwable t
              (log/errorf "Exception in server shutdown thread:\n%s"
                (with-out-str (print-cause-trace t))))))))
    (catch Throwable t
      (log/errorf "Exception when initiating server shutdown:\n%s"
        (with-out-str (print-cause-trace t))))))



(defn configure-logging
  "Configures the logging for BioTraq. Log level and log file can be specified in the configuration."
  [{:keys [log-level, log-file] :as config}]
  (let [props (doto (System/getProperties)
                (.setProperty "log4j.rootLogger" (format "%s, file" (-> log-level name str/upper-case)))
                (.setProperty "log4j.appender.file" "org.apache.log4j.RollingFileAppender")
                (.setProperty "log4j.appender.file.File" (str log-file))
                (.setProperty "log4j.appender.file.MaxFileSize" "4MB")
                (.setProperty "log4j.appender.file.MaxBackupIndex" "5")
                (.setProperty "log4j.appender.file.layout" "org.apache.log4j.PatternLayout")
                (.setProperty "log4j.appender.file.layout.ConversionPattern" "%d{yyyy.MM.dd HH:mm:ss} %5p %c: %m%n")
                ; jetty is too chatty
                (.setProperty "log4j.logger.org.eclipse.jetty" "INFO"))]
    (PropertyConfigurator/configure props))
  nil)


(defn- signal-available?
  []
  (try
    (Class/forName "sun.misc.Signal")
    true
    (catch Throwable t
      false)))


(defmacro signal-handler!
  [signal, handler-fn]
  (if (signal-available?) 
    `(try
       (sun.misc.Signal/handle (sun.misc.Signal. ~signal),
         (proxy [sun.misc.SignalHandler] []
           (handle [sig#] (~handler-fn sig#))))
       (catch IllegalArgumentException e#
         (log/errorf "Unable to set signal handler for signal: %s" ~signal)))
    `(log/errorf "Unable to set signal handlers. (signal: %s)" ~signal)))



(defn create-stop-handler
  [shutting-down?, signal]
  (fn [sig]
    (log/infof "Received %s signal.", signal)
    (when-let [stopped-fut (dosync
                             (when-not (ensure shutting-down?)
                               (alter shutting-down? (constantly true))
                               (log/infof "Stopping server (signal: %s) ...", signal)
                               (stop-server)))]
      (deref stopped-fut))))

(defn shutdown-on-sigterm!
  []
  (let [shutting-down? (ref false)]
    (let [sigterm-handler (create-stop-handler shutting-down?, "TERM")
          sigint-handler (create-stop-handler shutting-down?, "INT")]
      (signal-handler! "TERM", sigterm-handler)
      (signal-handler! "INT",  sigint-handler))))