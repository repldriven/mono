(ns com.repldriven.mono.bank-api.simulate.routes
  (:require
    [com.repldriven.mono.bank-api.simulate.handlers :as handlers]
    [com.repldriven.mono.telemetry.interface :as telemetry]))

(def routes
  [["/simulate"
    {:openapi {:tags ["Simulate"] :security [{"adminAuth" []}]}}
    ["/organizations/{org-id}/inbound-transfer"
     {:post {:summary "Simulate an inbound transfer"
             :openapi {:operationId "SimulateInboundTransfer"}
             :parameters {:path {:org-id string?}
                          :body [:ref
                                 "SimulateInboundTransferRequest"]}
             :interceptors [telemetry/require-idempotency-key]
             :responses {200 {:body [:ref
                                     "SimulateInboundTransferResponse"]}}
             :handler handlers/inbound-transfer}}]]])
