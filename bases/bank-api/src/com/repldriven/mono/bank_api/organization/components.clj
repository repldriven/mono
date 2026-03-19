(ns com.repldriven.mono.bank-api.organization.components
  (:require
    [com.repldriven.mono.bank-api.organization.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def CreateOrganizationRequest
  [:map {:json-schema/example examples/CreateOrganizationRequest}
   [:name string?]])

(def Organization
  [:map {:json-schema/example examples/Organization} [:organization-id string?]
   [:name string?] [:status string?]
   [:created-at {:optional true} [:maybe string?]]
   [:updated-at {:optional true} [:maybe string?]]])

(def OrganizationList
  [:map {:json-schema/example examples/OrganizationList}
   [:organizations [:vector [:ref "Organization"]]]])

(def CreateOrganizationResponse
  [:map {:json-schema/example examples/CreateOrganizationResponse}
   [:organization [:ref "Organization"]]
   [:api-key [:ref "CreateApiKeyResponse"]]])

(def registry
  (components-registry [#'CreateOrganizationRequest #'Organization
                        #'OrganizationList #'CreateOrganizationResponse]))
