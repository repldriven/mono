(ns com.repldriven.mono.bank-transaction.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(def ^:private type->status
  {:transaction-type-internal-transfer :transaction-status-posted})

(defn new-transaction
  "Creates a new transaction map. Internal transfers are
  posted immediately; all others start pending."
  [data]
  (let [now (System/currentTimeMillis)
        status (get type->status
                    (:transaction-type data)
                    :transaction-status-pending)]
    (-> (dissoc data :legs)
        (assoc :transaction-id (encryption/generate-id "txn")
               :status status
               :created-at now
               :updated-at now))))

(defn new-leg
  "Creates a new transaction leg map from input leg data,
  linking it to the given transaction-id and currency."
  [leg transaction-id currency]
  (assoc leg
         :leg-id (encryption/generate-id "leg")
         :transaction-id transaction-id
         :currency currency
         :created-at (System/currentTimeMillis)))
