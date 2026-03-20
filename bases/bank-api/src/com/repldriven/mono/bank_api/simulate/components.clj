(ns com.repldriven.mono.bank-api.simulate.components
  (:require
    [com.repldriven.mono.bank-api.simulate.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def SimulateInboundTransferRequest
  [:map {:json-schema/example examples/SimulateInboundTransferRequest}
   [:amount int?] [:currency [:ref "Currency"]]])

(def TransactionLeg
  [:map [:leg-id string?] [:transaction-id string?]
   [:account-id string?] [:balance-type string?]
   [:balance-status string?] [:side string?] [:amount int?]
   [:currency string?]
   [:created-at {:optional true} [:maybe string?]]])

(def SimulateInboundTransferResponse
  [:map {:json-schema/example examples/SimulateInboundTransferResponse}
   [:transaction-id string?] [:idempotency-key string?]
   [:status string?] [:transaction-type string?]
   [:currency string?]
   [:reference {:optional true} [:maybe string?]]
   [:created-at {:optional true} [:maybe string?]]
   [:updated-at {:optional true} [:maybe string?]]
   [:legs [:vector [:ref "TransactionLeg"]]]])

(def registry
  (components-registry [#'SimulateInboundTransferRequest #'TransactionLeg
                        #'SimulateInboundTransferResponse]))
