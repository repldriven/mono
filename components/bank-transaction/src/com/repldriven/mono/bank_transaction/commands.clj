(ns com.repldriven.mono.bank-transaction.commands
  (:require
    [com.repldriven.mono.bank-transaction.domain :as domain]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn- save-transaction
  [store transaction]
  (fdb/save-record store (schema/Transaction->java transaction)))

(defn- save-legs
  [store legs]
  (reduce (fn [_ leg]
            (let [result (fdb/save-record
                          store
                          (schema/TransactionLeg->java leg))]
              (when (error/anomaly? result)
                (reduced result))))
          nil
          legs))

(defn record
  "Records a transaction and legs within an open-store
  context. Does not update balances — callers must call
  apply-legs separately.
  Returns {:transaction <map> :legs [<maps>]} or anomaly."
  [open-store data]
  (let [{:keys [legs]} data
        transaction (domain/new-transaction data)
        {:keys [transaction-id currency]} transaction
        legs' (mapv (fn [leg]
                      (domain/new-leg leg transaction-id currency))
                    legs)]

    (let-nom>
      [transactions-store (open-store "transactions")
       transaction-legs-store (open-store "transaction-legs")
       _ (save-transaction transactions-store transaction)
       _ (save-legs transaction-legs-store legs')]
      (assoc transaction :legs legs'))))

(defn- transact-record+balances
  "Records a transaction with its legs in a single atomic
  transaction, applying legs to balances."
  [config data]
  (let [{:keys [record-db record-store]} config]
    (fdb/transact-multi
     record-db
     record-store
     (fn [open-store]
       (let-nom> [result (record open-store data)
                  balances-store (open-store "balances")
                  _ (balances/apply-legs balances-store (:legs result))]
         result)))))

(defn- ->response
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      (let-nom> [payload (avro/serialize (schemas "transaction") result)]
        {:status "ACCEPTED" :payload payload}))))

(defn record-transaction
  "Records a new transaction with its legs. Used by the
  command processor."
  [config data]
  (->response config (transact-record+balances config data)))
