(ns com.repldriven.mono.bank-api.balance.queries
  (:require
    [com.repldriven.mono.bank-api.errors :refer [error-response]]
    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account-product.interface :as
     cash-account-products]
    [com.repldriven.mono.error.interface :as error])
  (:import
    (java.time Instant)))

(defn- millis->iso [ms] (when (pos? ms) (str (Instant/ofEpochMilli ms))))

(defn- format-balance
  [balance]
  (-> balance
      (update :created-at millis->iso)
      (update :updated-at millis->iso)))

(defn list-balances
  [request]
  (let [{:keys [record-db record-store]} request
        account-id (get-in request [:parameters :path :account-id])
        result (balances/get-balances {:record-db record-db
                                       :record-store record-store}
                                      account-id)]
    (if (error/anomaly? result)
      {:status 500 :body (error-response 500 result)}
      {:status 200 :body {:balances (mapv format-balance result)}})))

(defn get-balance
  [request]
  (let [{:keys [record-db record-store]} request
        {:keys [account-id balance-type currency balance-status]}
        (get-in request [:parameters :path])
        result (balances/get-balance {:record-db record-db
                                      :record-store record-store}
                                     account-id
                                     balance-type
                                     currency
                                     balance-status)]
    (cond (error/anomaly? result)
          {:status 500
           :body (error-response 500 result)}
          (nil? result)
          {:status 404
           :body (error-response 404 "REJECTED"
                                 "balances/not-found"
                                 "Balance not found")}
          :else
          {:status 200 :body (format-balance result)})))

(defn list-balance-products
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        {:keys [product-id version-id]} (get-in request [:parameters :path])
        result (cash-account-products/get-version {:record-db record-db
                                                   :record-store record-store}
                                                  org-id
                                                  product-id
                                                  version-id)]
    (cond (error/anomaly? result)
          {:status 500
           :body (error-response 500 result)}
          (nil? result)
          {:status 404
           :body (error-response
                  404 "REJECTED"
                  "cash-account-products/version-not-found"
                  "Version not found")}
          :else
          {:status 200
           :body {:balance-products (:balance-products result)}})))
