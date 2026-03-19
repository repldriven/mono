(ns com.repldriven.mono.bank-balance.store
  (:refer-clojure :exclude [load])
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.bank-schema.interface :as schema]))

(defn save
  "Persists a balance. Returns nil or anomaly."
  [{:keys [record-db record-store]} balance]
  (error/try-nom :bank-balance/save
                 "Failed to save balance"
                 (fdb/transact
                  record-db
                  record-store
                  "balances"
                  (fn [store]
                    (fdb/save-record store (schema/Balance->java balance))))))

(defn load
  "Loads a balance by its composite primary key. Returns
  the balance map, nil if not found, or anomaly."
  [{:keys [record-db record-store]} account-id balance-type currency
   balance-status]
  (error/let-nom> [result
                   (error/try-nom :bank-balance/load
                                  "Failed to load balance"
                                  (fdb/transact record-db
                                                record-store
                                                "balances"
                                                (fn [store]
                                                  (fdb/load-record
                                                   store
                                                   account-id
                                                   (schema/balance-type->int
                                                    balance-type)
                                                   currency
                                                   (schema/balance-status->int
                                                    balance-status)))))]
    (when result (schema/pb->Balance result))))

(defn get-account-balances
  "Lists balances for an account. Returns a sequence of
  balance maps or anomaly."
  [{:keys [record-db record-store]} account-id]
  (error/try-nom :bank-balance/list
                 "Failed to list balances"
                 (fdb/transact record-db
                               record-store
                               "balances"
                               (fn [store]
                                 (mapv schema/pb->Balance
                                       (:records (fdb/scan-records
                                                  store
                                                  {:prefix [account-id]
                                                   :limit 100})))))))
