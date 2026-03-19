(ns com.repldriven.mono.bank-api.organization.examples
  (:require
    [com.repldriven.mono.bank-api.api-key.examples :as
     api-key-examples]
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def registry (examples-registry []))

(def Organization
  {:organization-id "org_01JMABC123"
   :name "Acme Corp"
   :status "ACTIVE"
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"})

(def OrganizationList {:organizations [Organization]})

(def CreateOrganizationRequest (select-keys Organization [:name]))

(def CreateOrganizationResponse
  {:organization Organization :api-key api-key-examples/CreateApiKey})
