(ns com.repldriven.mono.bank-api.api-key.components
  (:require
    [com.repldriven.mono.bank-api.api-key.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def ApiKeyResponse
  [:map {:json-schema/example examples/ApiKey} [:id string?]
   [:key-prefix string?] [:name string?]
   [:created-at {:optional true} [:maybe string?]]
   [:revoked-at {:optional true} [:maybe string?]]])

(def CreateApiKeyResponse
  [:map {:json-schema/example examples/CreateApiKey} [:id string?]
   [:key-prefix string?] [:name string?] [:raw-key string?]
   [:created-at {:optional true} [:maybe string?]]])

(def ApiKeyList
  [:map {:json-schema/example examples/ApiKeyList}
   [:api-keys [:vector [:ref "ApiKeyResponse"]]]])

(def registry
  (components-registry [#'ApiKeyResponse #'CreateApiKeyResponse #'ApiKeyList]))
