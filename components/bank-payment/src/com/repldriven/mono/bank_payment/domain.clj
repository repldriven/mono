(ns com.repldriven.mono.bank-payment.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(defn internal-payment->transaction
  "Builds the transaction data for an internal payment."
  [data]
  (let [{:keys [idempotency-key debtor-account-id
                creditor-account-id currency amount
                reference]}
        data]
    {:idempotency-key idempotency-key
     :transaction-type :transaction-type-internal-transfer
     :currency currency
     :reference reference
     :legs [{:account-id debtor-account-id
             :balance-type :balance-type-default
             :balance-status :balance-status-posted
             :side :leg-side-debit
             :amount amount}
            {:account-id creditor-account-id
             :balance-type :balance-type-default
             :balance-status :balance-status-posted
             :side :leg-side-credit
             :amount amount}]}))

(defn new-internal-payment
  "Creates a new internal payment map."
  [data transaction-id]
  (let [{:keys [idempotency-key debtor-account-id
                creditor-account-id currency amount
                reference]}
        data
        now (System/currentTimeMillis)]
    {:payment-id (encryption/generate-id "pmt")
     :idempotency-key idempotency-key
     :debtor-account-id debtor-account-id
     :creditor-account-id creditor-account-id
     :currency currency
     :amount amount
     :transaction-id transaction-id
     :reference reference
     :created-at now
     :updated-at now}))
