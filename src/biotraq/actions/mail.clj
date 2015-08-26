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

(ns biotraq.actions.mail
  (:require
    [clojure.string :as str]
    [clojure.stacktrace :refer [print-cause-trace]]
    [clojure.tools.logging :as log]
    [selmer.parser :as parser]
    [postal.core :as postal]
    [biotraq.config :as c]
    [biotraq.db.crud :as crud]
    [biotraq.actions.tools :as t])
  (:import
    javax.mail.SendFailedException))


(defn send-mail
  [mail-server-config, from, to, subject, body & {:keys [cc, error-context]}]
  (try
    (let [send (postal/send-message mail-server-config,
                 {:from from
                  :to to
                  :cc (when cc (mapv str/trim (str/split cc #",")))
                  :subject subject
                  :body    body})]
      (= :SUCCESS (:error send)))
    (catch SendFailedException e
      (let [invalid-addresses (some->> (.getInvalidAddresses e) (str/join ", " ))
            error-msg (format "%sE-mail notification could not be sent to all addresses.\n Invalid addresses: %s."
                        (if error-context (str error-context " ") "")
                        invalid-addresses)]
        (t/log!
          {:success 0, :action "E-mail Notification", :error error-msg})
        error-msg))
      (catch Exception e
        (let [error-msg (format "%sE-mail notification could not be sent. Error: %s"
                          (if error-context (str error-context " ") ""),
                          (.getMessage e))]
          (t/log! {:success 0, :action "E-mail Notification", :error error-msg })
          (log/errorf "%s. Exception:\n%s" error-msg, (with-out-str (print-cause-trace e)))
          error-msg))))



(defn send-project-info-mail
  [project, host-config, from, {:keys [to, subject, body, cc, error-context]}]
  (send-mail host-config, from, to,
    (parser/render subject, project),
    (parser/render body,    project),
    :cc cc,
    :error-context error-context))


(defn send-project-notification-mail
  "Send e-mail notification about project creation or project progress to the customer.
  (recipiet-type: customer, staff"
  [{:keys [id, trackingNr, notifycustomer] :as project}, notified-staff-list, notification-type]
  (try
    (if-not (#{:project-creation, :project-progress} notification-type)
      ; error logging
      (log/errorf "Sending project notification e-mail failed because there is no project notification of type %s." notification-type)
      ; do send
      (let [{:keys [host-config, from] :as mail-config} (c/mail-config),
            customers (crud/read-project-customers id)]
       ; customer notification, if the project has just been created or customer notification is specified
       (when (and (or (= notification-type :project-creation) (= notifycustomer 1)) (seq customers))
         (let [{:keys [subject, body] :as format-map} (get-in mail-config [notification-type :customer])]
           (doseq [{:keys [name, email]} customers]
             (send-project-info-mail (assoc project :customername name), host-config, from,
               (merge
                 format-map
                 {:to email
                  :error-context (format "Project \"%s\" notification for customer %s:" (:projectnumber project), name)})))))
       ; staff notification
       (when (seq notified-staff-list)
         (let [{:keys [subject, body] :as format-map} (get-in mail-config [notification-type :staff])]
           (doseq [{:keys [username, fullname, email]} notified-staff-list
                   :let [staffname (if (str/blank? fullname) username fullname)]]
             (send-project-info-mail (assoc project :staffname staffname), host-config, from,
               (merge
                 format-map
                 {:to email
                  :error-context (format "Project \"%s\" notification for BioTraq user %s:" (:projectnumber project) staffname)})))))))
    (catch Throwable t
      (log/errorf "Exception when trying to send project creation notification e-mails:\n%s"
        (with-out-str (print-cause-trace t))))))


(defn send-password-reset-mail
  "Send email with password reset link to the given email address of a user."
  [user-name, user-email, request-url]
  (if user-email
    (let [{:keys [host-config, from, subject, body]} (c/mail-config)]
       (send-mail host-config, from, user-email,
         "BioTraq Password Reset",
         (str "You can reset your password by using the following link:\n" request-url),
         :error-context (format "Password reset (user \"%s\", email: %s):" user-name, user-email)))
    (log/errorf "Could not send password reset e-mail to user %s because of missing e-mail address!" user-name)))