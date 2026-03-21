(ns com.repldriven.mono.bank-api.api-key.queries
  (:require
    [com.repldriven.mono.bank-api.errors :refer [error-response]]
    [com.repldriven.mono.bank-api-key.interface :as bank-api-key]
    [com.repldriven.mono.error.interface :as error]))

(defn get-api-keys
  [request]
  (let [org-id (get-in request [:auth :organization-id])
        config {:record-db (:record-db request)
                :record-store (:record-store request)}
        result (bank-api-key/get-api-keys config org-id)]
    (if (error/anomaly? result)
      {:status 500 :body (error-response 500 result)}
      {:status 200 :body {:api-keys result}})))
