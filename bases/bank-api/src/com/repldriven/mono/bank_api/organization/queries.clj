(ns com.repldriven.mono.bank-api.organization.queries
  (:require
    [com.repldriven.mono.bank-api.errors :refer [error-response]]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.bank-organization.interface :as organizations]))

(defn list-organizations
  [request]
  (let [config {:record-db (:record-db request)
                :record-store (:record-store request)}
        result (organizations/get-organizations config)]
    (if (error/anomaly? result)
      {:status 500 :body (error-response 500 result)}
      {:status 200
       :body {:organizations result}})))
