(ns com.repldriven.mono.bank-api.balance.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :refer [error-response]]
    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.error.interface :as error])
  (:import
    (java.time Instant)))

(defn- millis->iso [ms] (when (pos? ms) (str (Instant/ofEpochMilli ms))))

(defn- format-balance
  [balance]
  (-> balance
      (update :created-at millis->iso)
      (update :updated-at millis->iso)))

(defn create-balance
  [request]
  (let [{:keys [record-db record-store]} request
        account-id (get-in request [:parameters :path :account-id])
        body (get-in request [:parameters :body])
        result (balances/new-balance {:record-db record-db
                                      :record-store record-store}
                                     (assoc body :account-id account-id))]
    (if (error/anomaly? result)
      {:status 500 :body (error-response 500 result)}
      {:status 201 :body (format-balance result)})))
