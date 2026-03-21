(ns com.repldriven.mono.bank-cash-account.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [try-nom]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn get-accounts
  "Lists cash accounts for an organization. Returns a
  sequence of account maps or anomaly."
  [{:keys [record-db record-store]} org-id]
  (try-nom :cash-account/list
           "Failed to list accounts"
           (fdb/transact record-db
                         record-store
                         "cash-accounts"
                         (fn [store]
                           (mapv schema/pb->CashAccount
                                 (:records
                                  (fdb/scan-records
                                   store
                                   {:prefix [org-id]
                                    :limit 100})))))))
