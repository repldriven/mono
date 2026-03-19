(ns com.repldriven.mono.bank-api.cash-account-product.routes
  (:require
    [com.repldriven.mono.bank-api.cash-account-product.handlers :as handlers]
    [com.repldriven.mono.bank-api.cash-account-product.queries :as queries]
    [com.repldriven.mono.bank-api.cash-account-product.examples :refer
     [VersionNotFound NoPublishedVersion NotDraft]]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]))

(def routes
  [["/cash-account-products"
    {:openapi {:tags ["Cash Account Products"] :security [{"orgAuth" []}]}}
    [""
     {:get {:summary "Retrieve products"
            :openapi {:operationId "RetrieveCashAccountProducts"}
            :responses {200 {:body [:ref "CashAccountProductVersionList"]}}
            :handler queries/list-all-versions}
      :post {:summary "Draft a new product"
             :openapi {:operationId "CreateCashAccountProduct"}
             :parameters {:body [:ref "DraftCashAccountProductRequest"]}
             :responses {201 {:body [:ref "CashAccountProductVersion"]}}
             :handler handlers/create-product}}]
    ["/{product-id}" {:parameters {:path {:product-id string?}}}
     [""
      {:get {:summary "Retrieve published product"
             :openapi {:operationId "RetrieveCashAccountProduct"}
             :responses {200 {:body [:ref "CashAccountProductVersion"]}
                         404 (ErrorResponse [#'NoPublishedVersion])}
             :handler queries/get-published-version}}]
     ["/versions"
      [""
       {:get {:summary "Retrieve product versions"
              :openapi {:operationId "RetrieveCashAccountProductVersions"}
              :responses {200 {:body [:ref "CashAccountProductVersionList"]}}
              :handler queries/list-versions}
        :post {:summary "Draft a new product version"
               :openapi {:operationId "CreateCashAccountProductVersion"}
               :parameters {:body [:ref
                                   "DraftCashAccountProductVersionRequest"]}
               :responses {201 {:body [:ref "CashAccountProductVersion"]}}
               :handler handlers/create-version}}]
      ["/{version-id}" {:parameters {:path {:version-id string?}}}
       [""
        {:get {:summary "Retrieve a product version"
               :openapi {:operationId "RetrieveCashAccountProductVersion"}
               :responses {200 {:body [:ref "CashAccountProductVersion"]}
                           404 (ErrorResponse [#'VersionNotFound])}
               :handler queries/get-version}}]
       ["/publish"
        {:post {:summary "Publish a product version"
                :openapi {:operationId "PublishCashAccountProductVersion"}
                :responses {200 {:body [:ref "CashAccountProductVersion"]}
                            404 (ErrorResponse [#'VersionNotFound])
                            409 (ErrorResponse [#'NotDraft])}
                :handler handlers/publish-version}}]]]]]])
