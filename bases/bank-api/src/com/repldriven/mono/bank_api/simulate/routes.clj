(ns com.repldriven.mono.bank-api.simulate.routes
  (:require
    [com.repldriven.mono.bank-api.simulate.handlers :as handlers]
    [com.repldriven.mono.telemetry.interface :as telemetry]))

(def routes
  [["/simulate"
    {:openapi {:tags ["Simulate"] :security [{"adminAuth" []}]}}
    ["/organizations/{org-id}"
     {:parameters {:path {:org-id string?}}}
     ["/inbound-transfer"
      {:post {:summary "Simulate an inbound transfer"
              :openapi {:operationId "SimulateInboundTransfer"}
              :parameters {:body [:ref
                                  "SimulateInboundTransferRequest"]}
              :interceptors [telemetry/require-idempotency-key]
              :responses {200 {:body [:ref
                                      "SimulateInboundTransferResponse"]}}
              :handler handlers/inbound-transfer}}]
     ["/accrue"
      {:post {:summary "Accrue daily interest"
              :openapi {:operationId "SimulateAccrue"}
              :parameters {:body [:ref "SimulateInterestRequest"]}
              :interceptors [telemetry/require-idempotency-key]
              :responses {200 {:body [:ref
                                      "SimulateInterestResponse"]}}
              :handler handlers/accrue}}]
     ["/capitalize"
      {:post {:summary "Capitalize monthly interest"
              :openapi {:operationId "SimulateCapitalize"}
              :parameters {:body [:ref "SimulateInterestRequest"]}
              :interceptors [telemetry/require-idempotency-key]
              :responses {200 {:body [:ref
                                      "SimulateInterestResponse"]}}
              :handler handlers/capitalize}}]]]])
