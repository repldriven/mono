(ns com.repldriven.mono.bank-api.organization.components
  (:require
    [com.repldriven.mono.bank-api.organization.coercion :as coercion]
    [com.repldriven.mono.bank-api.organization.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def CreateOrganizationRequest
  [:map {:json-schema/example examples/CreateOrganizationRequest}
   [:name string?]
   [:currencies [:vector [:ref "Currency"]]]])

(def Organization
  [:map {:json-schema/example examples/Organization}
   [:organization-id string?]
   [:name string?]
   [:type
    [:enum
     {:json-schema coercion/organisation-type-json-schema
      :decode/api coercion/decode-organisation-type
      :encode/api coercion/encode-organisation-type}
     :organisation-type-internal :organisation-type-customer
     :organisation-type-unknown]]
   [:status string?]
   [:created-at {:optional true} [:maybe string?]]
   [:updated-at {:optional true} [:maybe string?]]
   [:party [:ref "Party"]]
   [:accounts [:vector [:ref "CashAccount"]]]
   [:api-key [:ref "ApiKeyResponse"]]])

(def OrganizationList
  [:map {:json-schema/example examples/OrganizationList}
   [:organizations [:vector [:ref "Organization"]]]])

(def CreateOrganizationResponse
  [:map {:json-schema/example examples/CreateOrganizationResponse}
   [:organization-id string?]
   [:name string?]
   [:type
    [:enum
     {:json-schema coercion/organisation-type-json-schema
      :decode/api coercion/decode-organisation-type
      :encode/api coercion/encode-organisation-type}
     :organisation-type-internal :organisation-type-customer
     :organisation-type-unknown]]
   [:status string?]
   [:created-at {:optional true} [:maybe string?]]
   [:updated-at {:optional true} [:maybe string?]]
   [:party [:ref "Party"]]
   [:accounts [:vector [:ref "CashAccount"]]]
   [:api-key [:ref "ApiKeyResponse"]]
   [:api-key-secret string?]])

(def registry
  (components-registry [#'CreateOrganizationRequest #'Organization
                        #'OrganizationList #'CreateOrganizationResponse]))
