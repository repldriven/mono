(ns com.repldriven.mono.bank-transaction.interface
  (:require
    com.repldriven.mono.bank-transaction.system

    [com.repldriven.mono.bank-transaction.commands :as commands]
    [com.repldriven.mono.bank-transaction.store :as store]))

(defn record-transaction
  "Records a transaction and legs within an open-store
  context. Does not update balances — callers must call
  apply-legs separately."
  [open-store data]
  (commands/record open-store data))

(defn get-account-transactions
  "Returns transaction legs for an account, enriched
  with parent transaction type, status, and reference."
  [config account-id]
  (store/get-account-transactions config account-id))
