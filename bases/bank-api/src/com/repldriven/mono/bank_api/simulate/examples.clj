(ns com.repldriven.mono.bank-api.simulate.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def SimulateInboundTransferRequest
  {:account-id "acc_01JMABC" :amount 1000 :currency "GBP"})

(def SimulateInboundTransferResponse
  {:transaction-id "txn_01JMABC"
   :idempotency-key "idem-001"
   :status "posted"
   :transaction-type "internal-transfer"
   :currency "GBP"
   :reference "Simulated inbound transfer"
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"
   :legs [{:leg-id "leg_01JMABC"
           :transaction-id "txn_01JMABC"
           :account-id "acc_01JMABC"
           :balance-type "suspense"
           :balance-status "posted"
           :side "debit"
           :amount 1000
           :currency "GBP"
           :created-at "2025-01-01T00:00:00Z"}
          {:leg-id "leg_02JMABC"
           :transaction-id "txn_01JMABC"
           :account-id "acc_02JMABC"
           :balance-type "default"
           :balance-status "posted"
           :side "credit"
           :amount 1000
           :currency "GBP"
           :created-at "2025-01-01T00:00:00Z"}]})

(def SimulateInterestRequest {:as-of-date 20260326})

(def SimulateInterestResponse
  {:organization-id "org_01JMABC" :as-of-date 20260326 :accounts-processed 5})

(def registry (examples-registry []))
