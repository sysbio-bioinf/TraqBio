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

(ns traqbio.actions.tools
  (:import (java.util Calendar)
           (java.text SimpleDateFormat))
  (:require
    [clojure.string :as str]
    [clojure.pprint :as pp]
    [clojure.stacktrace :refer [print-cause-trace]]
    [clojure.tools.logging :as log]
    [cemerick.friend :as friend]
    [selmer.parser :as tmpl]
    [traqbio.db.crud :as crud]))

; shamles copy from https://github.com/guv/clj-debug/blob/master/src/debug/intercept.clj
(defn process-defn-decl
  [func-symb, func-decl]
  (let [; extract doc string (if present) and create metadata map
         meta-map (if (string? (first func-decl))
                    {:doc (first func-decl)}
                    {}),
        ; remove doc string if present
         func-decl (if (string? (first func-decl))
                     (rest func-decl)
                     func-decl),
        ; add given metadata (if present) to map
         meta-map (if (map? (first func-decl))
                    (merge meta-map (first func-decl))
                    meta-map),
        ; remove metadata if present
         func-decl (if (map? (first func-decl))
                     (rest func-decl)
                     func-decl),
        ; if only a single function body then put it into a list
         func-decl (if (vector? (first func-decl))
                     (list func-decl)
                     func-decl)
        ; add given metadata at the end (if present) to map
         meta-map (if (map? (last func-decl))
                    (merge meta-map (last func-decl))
                    meta-map),
        ; remove metadata at the end if present
         func-decl (if (map? (last func-decl))
                     (butlast func-decl)
                     func-decl)
        ; merge metadata of the function name symbol with the collected metadata
         meta-map (merge (meta func-symb) meta-map)
         ]
      {:meta-map meta-map, :func-body-list func-decl}))

(defn now
  []
  (let [today (-> (Calendar/getInstance) .getTime)
        dateformat (SimpleDateFormat. "dd.MM.yyyy HH:mm")]
    (-> dateformat (.format today))))


(defn exception-message
  "Extract meaningful error messages."
  [^Throwable exception]
  (.getMessage exception))


(defn error-response?
  [response]
  (and
    (map? response)
    (let [{:keys [status, body]} response]
      (or
        (contains? body :error)
        (>= status 400)))))


(def ^:private http-status-map
  {400 "Bad Request"
   401 "Unauthorized"
   403 "Forbidden"
   404 "Not Found"
   500 "Internal Server Error"
   503 "Service Unavailable"})


(defn error-message
  [{status :status, {:keys [error]} :body, :as response}]  
  (let [status-msg (or (http-status-map status) (format "HTTP: %s" status))]
    (if error
      (format "%s (%s)" error status-msg)
      (format "(%s)" status-msg))))


(defn render-or-execute
  [tmpl-or-fn, param-result-map, captured-state, has-captured-value?]
  (when tmpl-or-fn
    (try
      (cond
        (string? tmpl-or-fn) (tmpl/render tmpl-or-fn, param-result-map),
        (fn? tmpl-or-fn)     (if has-captured-value?
                               (tmpl-or-fn param-result-map, captured-state)
                               (tmpl-or-fn param-result-map)))
      (catch Throwable t
        (log/errorf "Exception occured when trying to render or execute %s:\n%s"
          (cond-> tmpl-or-fn (fn? tmpl-or-fn) class)
          (with-out-str (print-cause-trace t)))))))


(defn log-map
  "Create a map to write in action-log"
  [fn-name, meta-map, has-captured-value?, {:keys [param-value-map, result, exception, captured-state]}]
  (try
    (let [{description-tmpl :description,
           message-tmpl     :message,
           error-tmpl       :error,
           projectid-tmpl       :projectid,
           action-type      :action-type} meta-map,        
          param-result-map {:parameters param-value-map :result result, :captured captured-state}
          error? (or exception (error-response? result))]
      (cond->
        {:success (if error? 0 1)
         :meta (str (dissoc meta-map :description :message :error))
         :action (if error?
                   (or
                     (render-or-execute error-tmpl, param-result-map, captured-state, has-captured-value?)
                     (format "Action %s failed" fn-name))
                   (or
                     (render-or-execute description-tmpl, param-result-map, captured-state, has-captured-value?)
                     (format "Action %s executed" fn-name)))
         :message (when-not error? (render-or-execute message-tmpl, param-result-map, captured-state, has-captured-value?))
         :args (pr-str param-value-map)}
        action-type
          (assoc :type (cond-> action-type (keyword? action-type) name))
        projectid-tmpl
          (assoc :projectid (render-or-execute projectid-tmpl, param-result-map, captured-state, has-captured-value?))
        error?
          (assoc :error (if exception
                          (exception-message exception)
                          (error-message result)))))
    (catch Throwable t
      (let [st (with-out-str (print-cause-trace t))]
        (log/errorf "Exception in log-map:\n%s" st)))))


(defn log!
  "Wirite map to action-log and provide user and timestamp"
  [m]
  (let [user (:username (friend/current-authentication)),
        date (now)]
    (when (or (zero? (:success m)) (:error m))
      (log/errorf "%s: %s" (:action m) (:error m)))
    (crud/insert-log
      (into m {:date date :username user}))))


(defn sanitize-args
  "Remove all destructuring from the arguments vector."
  [args]
  (reduce
    (fn [arg+destruct-pairs, a]
      (conj
        arg+destruct-pairs
        (if (symbol? a)
          [a a]
          (cond
            (map? a) (vector
                       ; symbol
                       (vary-meta
                         (get a :as (gensym "arg"))
                         into (meta a))
                       ; destructuring
                       (dissoc a :as))
            (vector? a) (let [as? (= (get a (- (count a) 2)) :as)]
                          (vector
                            ; symbol
                            (vary-meta
                              (if as? 
                                (peek a)
                                (gensym "arg"))
                              into (meta a))
                            ; destructuring
                            (cond->> a as? (->> (drop-last 2) vec))))
            :else (throw (IllegalArgumentException. (format "Illegal destructuring %s encountered in argument list %s!" a args)))))))
    []
    args))


(defn add-logging
  "Adds logging to an action.
  The name of the action, the date of execution and possible errors will be logged."
  [name meta-map [args & body]]
  (let [args+destruct-pairs (sanitize-args args),
        args (mapv first args+destruct-pairs),        
        param-value-map (zipmap (mapv (comp keyword clojure.core/name) args) args),
        fn-name (clojure.core/name name),
        capture (:capture meta-map)
        timing? (:timing meta-map)
        [begin-time end-time] (when timing? [(gensym "begin-time") (gensym "end-time")])
        meta-map (dissoc meta-map :capture :timing)
        captured-state (gensym "captured")]
    `(~args
      (let [param-value-map# ~param-value-map,]
        (try
          (let [~@(when timing? [begin-time `(System/currentTimeMillis)]),
                ; insert destructuring from args vector
                ~@(mapcat reverse args+destruct-pairs),
                ; capture state if specified, might use fn arg names
                ~@(when capture [captured-state `(try ~capture
                                                   (catch Throwable e#
                                                     (log/errorf "Error occured during :capture of %s:\n%s"
                                                       ~(str name)
                                                       (with-out-str (print-cause-trace e#)))))])]
            (try
              (let [response# (do ~@body),
                    _# (log! (log-map ~fn-name, ~meta-map, ~(boolean capture), {:param-value-map param-value-map#, :result response#, :captured-state ~(when capture captured-state)})),
                    ~@(when timing? [end-time `(System/currentTimeMillis)])]
                ~(when timing?
                   `(log/infof "Timing: action %s took %d ms." ~fn-name (- ~end-time ~begin-time)))
                response#)
              (catch Throwable e#
                (log! (log-map ~fn-name, ~meta-map, ~(boolean capture), {:param-value-map param-value-map#, :exception e#, :captured-state ~(when capture captured-state)}))
                (log/errorf "Exception occured during execution of action \"%s\":\n%s"
                  ~(str name), (with-out-str (print-cause-trace e#)))
                {:status 500, :body {:error (exception-message e#)} })))
          (catch Throwable e#
            (log! (log-map ~fn-name, ~meta-map,  ~(boolean capture), {:param-value-map param-value-map#, :exception e#, :implementation-error true}))
            {:status 500, :body {:error (format "Implementation error in action %s: %s" name, (exception-message e#))}}))))))

(defmacro defaction
  "Macro to create a action. A action wraps a function and logs invocations of this function.
  The :description of the action that is shown in the timeline can be given."
  [name & decl]
  (let [{:keys [func-body-list, meta-map]} (process-defn-decl name, decl)
         func-body-list (mapv (partial add-logging name meta-map) func-body-list)]
    `(defn ~(with-meta name (dissoc meta-map :capture :description :message :error :timing :projectid))
       ~@func-body-list)))


(defn nonempty-string
  [x]
  (when-not (and (string? x) (str/blank? x))
    x))

(defn equal?
  "Compares the two given data items for equality.
  Blank strings and nil are treated as equal"
  [x, y]
  (= (nonempty-string x) (nonempty-string y)))