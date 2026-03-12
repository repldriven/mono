(ns com.repldriven.mono.bank-api.organizations.queries
  (:require
    [com.repldriven.mono.bank-api.errors :refer [error-response]]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schemas.interface :as schema])
  (:import
    (java.time Instant)))

(defn- millis->iso
  [ms]
  (when (pos? ms) (str (Instant/ofEpochMilli ms))))

(defn- format-timestamps
  [org]
  (-> org
      (update :created-at millis->iso)
      (update :updated-at millis->iso)))

(defn list-organizations
  [request]
  (let [{:keys [record-db record-store]} request
        result (fdb/transact record-db
                             record-store
                             "organizations"
                             (fn [store]
                               (fdb/scan-records
                                store
                                {:limit 100})))]
    (if (error/anomaly? result)
      {:status 500 :body (error-response 500 result)}
      {:status 200
       :body {:organizations
              (mapv (comp format-timestamps
                          schema/pb->Organization)
                    (:records result))}})))
