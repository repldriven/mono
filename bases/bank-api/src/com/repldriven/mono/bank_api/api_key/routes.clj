(ns com.repldriven.mono.bank-api.api-key.routes
  (:require
    [com.repldriven.mono.bank-api.api-key.queries :as queries]))

(def routes
  [["/api-keys" {:openapi {:tags ["API Keys"] :security [{"orgAuth" []}]}}
    [""
     {:get {:summary "Retrieve API keys"
            :openapi {:operationId "RetrieveApiKeys"}
            :responses {200 {:body [:ref "ApiKeyList"]}}
            :handler queries/list-api-keys}}]]])
