(ns com.repldriven.mono.bank-transaction.commands
  (:require
    [com.repldriven.mono.bank-transaction.domain :as domain]

    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn- save-transaction
  "Saves transaction to store, returns protobuf record or
  anomaly."
  [store transaction]
  (let-nom>
    [_ (fdb/save-record store (schema/Transaction->java transaction))]
    (schema/Transaction->pb transaction)))

(defn- save-leg
  "Saves a transaction leg to store."
  [store leg]
  (fdb/save-record store (schema/TransactionLeg->java leg)))

(defn- update-balance
  "Loads a balance by composite key and increments the debit
  or credit field by amount. Returns nil or anomaly."
  [balance-store leg]
  (let [{:keys [account-id balance-type currency balance-status side amount]}
        leg
        record (fdb/load-record balance-store
                                account-id
                                (schema/balance-type->int balance-type)
                                currency
                                (schema/balance-status->int balance-status))]
    (when record
      (let [balance (schema/pb->Balance record)
            field (if (= :leg-side-debit side) :debit :credit)
            updated (update balance field + amount)]
        (fdb/save-record balance-store (schema/Balance->java updated))))))

(defn- update-balances
  "Updates balances for all legs. Returns nil or anomaly."
  [balance-store legs]
  (reduce (fn [_ leg]
            (let [result (update-balance balance-store leg)]
              (if (error/anomaly? result) (reduced result) result)))
          nil
          legs))

(defn- record
  "Records a transaction with its legs in a single atomic
  transaction, updating balances for each leg. Returns
  protobuf transaction or anomaly."
  [config data]
  (let [{:keys [record-db record-store]} config]
    (fdb/transact-multi
     record-db
     record-store
     (fn [open-store]
       (let [{:keys [legs]} data
             txn (domain/new-transaction data)
             txn-id (:transaction-id txn)
             currency (:currency txn)
             legs (mapv (fn [leg] (domain/new-leg leg txn-id currency))
                        legs)
             txn-store (open-store "transactions")
             leg-store (open-store "transaction-legs")
             balance-store (open-store "balances")]
         (let-nom>
           [transaction (save-transaction txn-store txn)
            _ (reduce (fn [_ leg]
                        (let [result (save-leg leg-store leg)]
                          (if (error/anomaly? result) (reduced result) result)))
                      nil
                      legs)
            _ (update-balances balance-store legs)]
           {:transaction transaction :legs legs}))))))

(defn- ->response
  "Converts a protobuf record to an ACCEPTED response.
  Returns anomalies unchanged for the processor to handle."
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config
          {:keys [transaction legs]} result
          txn (schema/pb->Transaction transaction)]
      (let-nom> [payload
                 (avro/serialize (schemas "transaction")
                                 (assoc txn :legs legs))]
        {:status "ACCEPTED" :payload payload}))))

(defn record-transaction
  "Records a new transaction with its legs."
  [config data]
  (->response config (record config data)))
