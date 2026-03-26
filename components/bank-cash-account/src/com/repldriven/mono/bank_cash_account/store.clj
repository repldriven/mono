(ns com.repldriven.mono.bank-cash-account.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [let-nom> try-nom]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn get-accounts
  "Lists cash accounts for an organization. Returns
  {:accounts [maps] :before id|nil :after id|nil} or
  anomaly. opts supports :after, :before, :limit."
  [{:keys [record-db record-store]} org-id opts]
  (let-nom>
    [result
     (try-nom :cash-account/list
              "Failed to list accounts"
              (fdb/transact record-db
                            record-store
                            "cash-accounts"
                            (fn [store]
                              (fdb/scan-records
                               store
                               (merge {:prefix [org-id]
                                       :limit 100}
                                      opts)))))
     {:keys [records before after]} result]
    {:accounts (mapv schema/pb->CashAccount records)
     :before before
     :after after}))

(defn get-accounts-by-type
  "Returns accounts matching the given account-type.
  Uses the account_type_idx secondary index."
  [{:keys [record-db record-store]} account-type]
  (try-nom :cash-account/list-by-type
           "Failed to list accounts by type"
           (fdb/transact
            record-db
            record-store
            "cash-accounts"
            (fn [store]
              (mapv schema/pb->CashAccount
                    (fdb/query-records
                     store
                     "CashAccount"
                     "account_type"
                     (schema/account-type->pb-enum
                      account-type)))))))
