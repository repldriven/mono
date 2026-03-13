(ns com.repldriven.mono.bank-api.api-keys.routes
  (:require
    [com.repldriven.mono.bank-api.api-keys.queries :as queries]))

(def routes
  [["/api-keys"
    {:openapi {:tags ["API Keys"] :security [{"orgAuth" []}]}}
    [""
     {:get {:summary "List API keys"
            :openapi {:operationId "ListApiKeys"}
            :responses {200 {:body [:ref "ApiKeyList"]}}
            :handler queries/list-api-keys}}]]])
