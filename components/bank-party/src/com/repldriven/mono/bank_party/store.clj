(ns com.repldriven.mono.bank-party.store
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.bank-schema.interface :as schema]))

(defn get-parties
  "Lists parties for an organization. Returns a sequence of
  party maps or anomaly."
  [{:keys [record-db record-store]} org-id]
  (error/try-nom :bank-party/list
                 "Failed to list parties"
                 (fdb/transact record-db
                               record-store
                               "parties"
                               (fn [store]
                                 (mapv schema/pb->Party
                                       (:records
                                        (fdb/scan-records
                                         store
                                         {:prefix [org-id]
                                          :limit 100})))))))
