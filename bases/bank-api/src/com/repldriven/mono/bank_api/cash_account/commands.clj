(ns com.repldriven.mono.bank-api.cash-account.commands
  (:require
    [com.repldriven.mono.bank-api.commands :as commands]))

(defn- dispatcher [request] (get-in request [:dispatchers :cash-accounts]))

(defn open-cash-account
  [request]
  (commands/send (dispatcher request)
                 request
                 "open-cash-account"
                 "cash-account"
                 (assoc (get-in request [:parameters :body])
                        :organization-id
                        (get-in request [:auth :organization-id]))))

(defn close-cash-account
  [request]
  (let [{:keys [account-id]} (get-in request [:parameters :path])
        org-id (get-in request [:auth :organization-id])]
    (commands/send (dispatcher request)
                   request
                   "close-cash-account"
                   "cash-account"
                   {:organization-id org-id
                    :account-id account-id})))
