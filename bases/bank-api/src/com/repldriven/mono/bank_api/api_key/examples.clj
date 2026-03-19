(ns com.repldriven.mono.bank-api.api-key.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def ApiKey
  {:id "sk_01JMABC123"
   :key-prefix "sk_live_abc1"
   :name "default"
   :created-at "2025-01-01T00:00:00Z"})

(def CreateApiKey
  {:id "sk_01JMABC123"
   :key-prefix "sk_live_abc1"
   :name "default"
   :raw-key "sk_live_abc123def456"
   :created-at "2025-01-01T00:00:00Z"})

(def ApiKeyList {:api-keys [ApiKey]})

(def registry (examples-registry [#'ApiKeyList]))
