(ns com.repldriven.mono.bank-transaction.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(def ^:private type->status
  {:transaction-type-internal-transfer :transaction-status-posted})

(defn new-transaction
  "Creates a new transaction map. Internal transfers are
  posted immediately; all others start pending."
  [data]
  (let [{:keys [idempotency-key transaction-type currency
                reference]}
        data
        now (System/currentTimeMillis)
        status (get type->status
                    transaction-type
                    :transaction-status-pending)]
    {:transaction-id (encryption/generate-id "txn")
     :idempotency-key idempotency-key
     :transaction-type transaction-type
     :currency currency
     :reference reference
     :status status
     :created-at now
     :updated-at now}))

(defn new-leg
  "Creates a new transaction leg map from input leg data,
  linking it to the given transaction-id and currency."
  [leg transaction-id currency]
  (let [{:keys [account-id balance-type balance-status
                side amount]}
        leg]
    {:leg-id (encryption/generate-id "leg")
     :transaction-id transaction-id
     :account-id account-id
     :balance-type balance-type
     :balance-status balance-status
     :side side
     :amount amount
     :currency currency
     :created-at (System/currentTimeMillis)}))
