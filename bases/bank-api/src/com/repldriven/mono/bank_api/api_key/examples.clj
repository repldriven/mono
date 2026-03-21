(ns com.repldriven.mono.bank-api.api-key.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def ApiKey
  {:id "sk_01JMABC123"
   :name "default"
   :key-prefix "sk_live_abc1"
   :created-at "2025-01-01T00:00:00Z"})

(def ApiKeyList {:api-keys [ApiKey]})

(def ApiKeySecret "sk_live_abc123def456")

(def CreateApiKeyResponse (assoc ApiKey :key-secret ApiKeySecret))

(def registry
  (examples-registry [#'ApiKey #'ApiKeySecret #'ApiKeyList
                      #'CreateApiKeyResponse]))
