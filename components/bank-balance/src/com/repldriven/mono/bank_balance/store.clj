(ns com.repldriven.mono.bank-balance.store
  (:refer-clojure :exclude [load])
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error
     :refer [let-nom> try-nom]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn save
  "Persists a balance. Returns nil or anomaly."
  [{:keys [record-db record-store]} balance]
  (try-nom :balance/save
           "Failed to save balance"
           (fdb/transact
            record-db
            record-store
            "balances"
            (fn [store]
              (fdb/save-record store (schema/Balance->java balance))))))

(defn- load
  "Loads a balance by its composite primary key. Returns
  the balance record, nil if not found, or anomaly."
  [{:keys [record-db record-store]} account-id balance-type currency
   balance-status]
  (try-nom :balance/load
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
                             balance-status))))))

(defn get-balance
  "Loads a balance by its composite primary key. Returns
  the balance, nil if not found, or anomaly."
  [config account-id balance-type currency
   balance-status]
  (let-nom> [result
             (load config account-id balance-type currency balance-status)]
    (when result (schema/pb->Balance result))))

(defn- apply-leg
  "Updates a balance for a transaction leg. Loads the
  balance by composite key, increments credit or debit
  based on side, saves. For use inside an open store."
  [store leg]
  (let [{:keys [account-id balance-type currency
                balance-status side amount]}
        leg
        record (fdb/load-record
                store
                account-id
                (schema/balance-type->int balance-type)
                currency
                (schema/balance-status->int
                 balance-status))]
    (when record
      (let [balance (schema/pb->Balance record)
            field (if (= :leg-side-debit side)
                    :debit
                    :credit)
            updated (update balance field + amount)]
        (fdb/save-record store
                         (schema/Balance->java updated))))))

(defn apply-legs
  "Applies all legs to balances. For use inside an open
  store. Returns nil or the first anomaly."
  [store legs]
  (reduce (fn [_ leg]
            (let [result (apply-leg store leg)]
              (when (error/anomaly? result)
                (reduced result))))
          nil
          legs))

(defn get-account-balances
  "Lists balances for an account. Returns a sequence of
  balances or anomaly."
  [{:keys [record-db record-store]} account-id]
  (try-nom :balance/list
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
