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

(ns traqbio.actions.user
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]
    [clojure.tools.logging :as log]
    [cemerick.friend :as friend]
    [traqbio.config :as c]
    [traqbio.db.crud :as crud]
    [traqbio.actions.tools :as t]
    [traqbio.actions.mail :as mail]))

      
(def user-attributes
  {:password "Password"
   :fullname "Full name"
   :email "E-Mail"})

(defn- user-role
  [user]
  (some-> user :role keyword name))
      

(defn- user-diff
  [p+r-map, old-user]
  (let [new-user (get-in p+r-map [:parameters, :user]),
        changed-attributes (seq
                          (keep
                            #(when-not (t/equal? (% old-user) (% new-user))
                               (% user-attributes))
                            [:password, :fullname :email])),
        ; if no role is sent, then the configadmin was updated (his role is not modifiable)
        role-changed? (and (:role new-user) (not= (:role old-user) (:role new-user)))
        diff (cond-> []
               role-changed?
                 (conj (format "User role has changed to \"%s\"." (user-role new-user)))
               (and role-changed? changed-attributes)
                 (conj "")
               changed-attributes
                 (conj (format "Changed user attributes: %s" (str/join ", " changed-attributes)) ))]
    (when (seq diff)
      (str/join "\n" diff))))


(t/defaction update-user
  "Updates a given user"
  {:description "User \"{{parameters.user.username}}\" updated",
   :message user-diff,
   :capture (crud/read-user username),
   :error "Failed to update user \"{{parameters.user.username}}\"",
   :action-type :update}
  [{:keys [username] :as user}]
  (let [editing-role (:role (friend/current-authentication)),
        user-role (edn/read-string (:role (crud/read-user username)))]
    (if (or (not= user-role :traqbio.config/configadmin) (= editing-role :traqbio.config/configadmin))
      (if (crud/put-user user)
        {:body user :status 200}
        {:body {:error (format "There is no user named \"%s\"." username)} :status 404})
      {:status 403, :body {:error "You are not allowed to modify that user."}})))


(t/defaction create-user
  "Creates a new user"
  {:description "User \"{{parameters.user.username}}\" created",
   :message "role: {{captured}}",
   :capture (user-role user),
   :error "Failed to create user \"{{parameters.user.username}}\"",
   :action-type :create}
  [{:keys [username] :as user}]
  (if (crud/has-user? username)
    {:body {:error (str "User " username " already exists.")} :status 403}
    (if (crud/put-user user)
      {:body user :status 201}
      {:status 500})))


(t/defaction delete-user
  "Deletes a given user"
  {:description "User \"{{parameters.username}}\" deleted",
   :error "Failed to delete user \"{{parameters.username}}\"",
   :action-type :delete}
  [username]
  (cond
    (and (= (:role (crud/read-user username)) :traqbio.config/configadmin) (not= (:role (friend/current-authentication)) :traqbio.config/configadmin))
      {:status 403, :body {:error "You are not allowed to delete that user."}}
    (= (:username (friend/current-authentication)) username)
      {:status 403, :body {:error "You must not delete yourself."}}
    (= 1 (first (crud/delete-user username)))
      {:body {:username username} :status 204}
    :else
      {:status 404}))


(defn- render-password-reset-link
  [request-id]
  (str "http://" (c/tracking-server-domain) (c/server-location "/reset/") request-id))


(defn request-password-reset
  "Request a password password of the given user."
  [{:keys [params], :as request}]
  (let [{given-username :username, given-email :email} params,
        missing-data? (or (str/blank? given-username) (str/blank? given-email))]
    (if missing-data?
      (do
        (log/debugf "Missing username or e-mail for password reset!")
        {:missing-data? true})
      (if-let [{:keys [username, email] :as user} (crud/read-user given-username)]
        (if (= (str/lower-case given-email) (str/lower-case email))
          (do
            (log/debugf "Password reset requested for user \"%s\" with e-mail address \"%s\"." username, email)        
            (let [reset-request-id (crud/create-password-reset-request username)]
              (mail/send-password-reset-mail username, email, (render-password-reset-link reset-request-id))
              {:success? true}))
          (do
            (log/debugf "Password reset requested for user \"%s\" failed because of wrong e-mail address \"%s\"." username, given-email)
            {:success? false}))
        (do
          (log/debugf "Password reset request failed - there is no user called \"%s\"!" given-username)
          {:success? false})))))





(defn reset-password
  [{{:keys [password]} :params, :as request}, {:keys [username, resetrequestid] :as reset-data}]
  (if (str/blank? password)
    {:successful-reset? false, :error-message "Blank passwords are not allowed!"}
    (if (crud/complete-password-reset resetrequestid, username, password)
      {:successful-reset? true}
      {:successful-reset? false, :error-message "Password reset request has already been completed!"})))