(ns com.repldriven.mono.bank-api.organization.routes
  (:require
    [com.repldriven.mono.bank-api.organization.handlers :as handlers]
    [com.repldriven.mono.bank-api.organization.queries :as queries]))

(def routes
  [["/organizations"
    {:openapi {:tags ["Organizations"] :security [{"adminAuth" []}]}}
    [""
     {:get {:summary "Retrieve organizations"
            :openapi {:operationId "RetrieveOrganizations"}
            :responses {200 {:body [:ref "OrganizationList"]}}
            :handler queries/list-organizations}
      :post {:summary "Create a new organization"
             :openapi {:operationId "CreateOrganization"}
             :parameters {:body [:ref "CreateOrganizationRequest"]}
             :responses {201 {:body [:ref "CreateOrganizationResponse"]}}
             :handler handlers/create-organization}}]]])
