(ns com.repldriven.mono.bank-api.simulate.components
  (:require
    [com.repldriven.mono.bank-api.simulate.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def SimulateInboundTransferRequest
  [:map {:json-schema/example examples/SimulateInboundTransferRequest}
   [:account-id string?]
   [:amount int?]
   [:currency [:ref "Currency"]]])

(def TransactionLeg
  [:map
   [:leg-id string?]
   [:transaction-id string?]
   [:account-id string?]
   [:balance-type [:ref "BalanceType"]]
   [:balance-status [:ref "BalanceStatus"]]
   [:side [:ref "LegSide"]]
   [:amount int?]
   [:currency string?]
   [:created-at {:optional true} [:maybe [:ref "Timestamp"]]]])

(def SimulateInboundTransferResponse
  [:map {:json-schema/example examples/SimulateInboundTransferResponse}
   [:transaction-id string?]
   [:idempotency-key string?]
   [:status [:ref "TransactionStatus"]]
   [:transaction-type [:ref "TransactionType"]]
   [:currency string?]
   [:reference {:optional true} [:maybe string?]]
   [:created-at {:optional true} [:maybe [:ref "Timestamp"]]]
   [:updated-at {:optional true} [:maybe [:ref "Timestamp"]]]
   [:legs [:vector [:ref "TransactionLeg"]]]])

(def SimulateInterestRequest
  [:map {:json-schema/example examples/SimulateInterestRequest}
   [:as-of-date int?]])

(def SimulateInterestResponse
  [:map {:json-schema/example examples/SimulateInterestResponse}
   [:organization-id string?]
   [:as-of-date int?]
   [:accounts-processed int?]])

(def registry
  (components-registry [#'SimulateInboundTransferRequest #'TransactionLeg
                        #'SimulateInboundTransferResponse
                        #'SimulateInterestRequest #'SimulateInterestResponse]))
