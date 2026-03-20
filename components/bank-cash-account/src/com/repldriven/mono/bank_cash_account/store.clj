(ns com.repldriven.mono.bank-cash-account.store
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.bank-schema.interface :as schema]))

(defn get-accounts
  "Lists cash accounts for an organization. Returns a
  sequence of account maps or anomaly."
  [{:keys [record-db record-store]} org-id]
  (error/try-nom :bank-cash-account/list
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
