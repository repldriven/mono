(ns com.repldriven.mono.bank-api.api-key.components
  (:require
    [com.repldriven.mono.bank-api.api-key.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def ApiKey
  [:map {:json-schema/example examples/ApiKey}
   [:id string?]
   [:name string?]
   [:key-prefix string?]
   [:created-at {:optional true} [:maybe [:ref "Timestamp"]]]])

(def ApiKeyList
  [:map {:json-schema/example examples/ApiKeyList}
   [:api-keys [:vector [:ref "ApiKey"]]]])

(def CreateApiKeyResponse
  [:map {:json-schema/example examples/CreateApiKeyResponse}
   [:id string?]
   [:name string?]
   [:key-prefix string?]
   [:key-secret string?]
   [:created-at {:optional true} [:maybe [:ref "Timestamp"]]]])

(def registry
  (components-registry [#'ApiKey #'ApiKeyList #'CreateApiKeyResponse]))
