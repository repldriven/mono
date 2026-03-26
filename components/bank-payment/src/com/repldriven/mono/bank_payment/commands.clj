(ns com.repldriven.mono.bank-payment.commands
  (:require
    [com.repldriven.mono.bank-payment.domain :as domain]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-schema.interface :as schema]
    [com.repldriven.mono.bank-transaction.interface :as
     transactions]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn- ->response
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      (let-nom> [payload
                 (avro/serialize (schemas "internal-payment")
                                 result)]
        {:status "ACCEPTED" :payload payload}))))

(defn submit-internal
  "Submits an internal payment. Records the underlying
  transaction and persists the payment record atomically."
  [config data]
  (->response
   config
   (let [{:keys [record-db record-store]} config]
     (fdb/transact-multi
      record-db
      record-store
      (fn [open-store]
        (let-nom>
          [balances-store (open-store "balances")
           internal-payments-store (open-store "internal-payments")
           payment-transaction (domain/internal-payment->transaction data)
           transaction (transactions/record-transaction open-store
                                                        payment-transaction)
           {:keys [transaction-id legs]} transaction
           _ (balances/apply-legs balances-store legs)
           payment (domain/new-internal-payment data transaction-id)
           _ (fdb/save-record internal-payments-store
                              (schema/InternalPayment->java payment))]
          payment))))))
