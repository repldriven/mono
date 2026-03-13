(ns com.repldriven.mono.bank-api.organizations.routes
  (:require
    [com.repldriven.mono.bank-api.organizations.handlers :as handlers]
    [com.repldriven.mono.bank-api.organizations.queries :as queries]))

(def routes
  [["/organizations"
    {:openapi {:tags ["Organizations"] :security [{"adminAuth" []}]}}
    [""
     {:get {:summary "List organizations"
            :openapi {:operationId "ListOrganizations"}
            :responses {200 {:body [:ref "OrganizationList"]}}
            :handler queries/list-organizations}
      :post {:summary "Create an organization"
             :openapi {:operationId "CreateOrganization"}
             :parameters {:body [:ref "CreateOrganizationRequest"]}
             :responses {201 {:body [:ref "CreateOrganizationResponse"]}}
             :handler handlers/create-organization}}]]])
