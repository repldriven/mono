(ns com.repldriven.mono.bank-api.account-products.routes
  (:require
    [com.repldriven.mono.bank-api.account-products.handlers
     :as handlers]
    [com.repldriven.mono.bank-api.account-products.queries
     :as queries]
    [com.repldriven.mono.bank-api.account-products.examples
     :refer [VersionNotFound NoPublishedVersion NotDraft]]
    [com.repldriven.mono.bank-api.schema
     :refer [ErrorResponse]]))

(def routes
  [["/account-products"
    {:openapi {:tags ["Account Products"] :security [{"orgAuth" []}]}}
    [""
     {:get {:summary "Retrieve all products"
            :openapi {:operationId "ListAllAccountProductVersions"}
            :responses {200 {:body [:ref "AccountProductVersionList"]}}
            :handler queries/list-all-versions}
      :post {:summary "Draft a new product"
             :openapi {:operationId "CreateAccountProduct"}
             :parameters {:body [:ref "DraftAccountProductRequest"]}
             :responses {201 {:body [:ref "AccountProductVersion"]}}
             :handler handlers/create-product}}]
    ["/{product-id}"
     {:parameters {:path {:product-id string?}}}
     [""
      {:get {:summary "Retrieve published product"
             :openapi {:operationId "GetAccountProduct"}
             :responses {200 {:body [:ref "AccountProductVersion"]}
                         404 (ErrorResponse [#'NoPublishedVersion])}
             :handler queries/get-published-version}}]
     ["/versions"
      [""
       {:get {:summary "Retrieve product versions"
              :openapi {:operationId "ListAccountProductVersions"}
              :responses {200 {:body [:ref "AccountProductVersionList"]}}
              :handler queries/list-versions}
        :post {:summary "Draft a new product version"
               :openapi {:operationId "CreateAccountProductVersion"}
               :parameters {:body [:ref "DraftAccountProductVersionRequest"]}
               :responses {201 {:body [:ref "AccountProductVersion"]}}
               :handler handlers/create-version}}]
      ["/{version-id}"
       {:parameters {:path {:version-id string?}}}
       [""
        {:get {:summary "Retrieve a product version"
               :openapi {:operationId "GetAccountProductVersion"}
               :responses {200 {:body [:ref "AccountProductVersion"]}
                           404 (ErrorResponse [#'VersionNotFound])}
               :handler queries/get-version}}]
       ["/publish"
        {:post {:summary "Publish a product version"
                :openapi {:operationId "PublishAccountProductVersion"}
                :responses {200 {:body [:ref "AccountProductVersion"]}
                            404 (ErrorResponse [#'VersionNotFound])
                            409 (ErrorResponse [#'NotDraft])}
                :handler handlers/publish-version}}]]]]]])
