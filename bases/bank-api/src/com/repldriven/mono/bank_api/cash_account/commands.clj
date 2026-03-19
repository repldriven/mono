(ns com.repldriven.mono.bank-api.cash-account.commands
  (:require
    [com.repldriven.mono.bank-api.commands :as commands])
  (:import
    (java.time Instant)))

(defn- millis->iso [ms] (when (pos? ms) (str (Instant/ofEpochMilli ms))))

(defn- format-timestamps
  [account]
  (-> account
      (update :created-at millis->iso)
      (update :updated-at millis->iso)))

(defn- format-account-response
  [{:keys [status body] :as response}]
  (if (= 200 status) (assoc response :body (format-timestamps body)) response))

(defn- dispatcher [request] (get-in request [:dispatchers :cash-accounts]))

(defn open-cash-account
  [request]
  (format-account-response (commands/send
                            (dispatcher request)
                            request
                            "open-cash-account"
                            "cash-account"
                            (assoc (get-in request [:parameters :body])
                                   :organization-id
                                   (get-in request [:auth :organization-id])))))

(defn close-cash-account
  [request]
  (let [{:keys [account-id]} (get-in request [:parameters :path])
        org-id (get-in request [:auth :organization-id])]
    (format-account-response (commands/send (dispatcher request)
                                            request
                                            "close-cash-account"
                                            "cash-account"
                                            {:organization-id org-id
                                             :account-id account-id}))))
