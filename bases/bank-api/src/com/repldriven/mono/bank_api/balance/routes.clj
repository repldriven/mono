(ns com.repldriven.mono.bank-api.balance.routes
  (:require
    [com.repldriven.mono.bank-api.balance.coercion :as coercion]
    [com.repldriven.mono.bank-api.balance.handlers :as handlers]
    [com.repldriven.mono.bank-api.balance.queries :as queries]
    [com.repldriven.mono.bank-api.balance.examples :refer
     [BalanceNotFound]]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]))

(def routes
  [["/cash-accounts/{account-id}/balances"
    {:openapi {:tags ["Balances"] :security [{"orgAuth" []}]}
     :parameters {:path {:account-id string?}}}
    [""
     {:get {:summary "Retrieve account balances"
            :openapi {:operationId "RetrieveBalances"}
            :responses {200 {:body [:ref "BalanceList"]}}
            :handler queries/list-balances}
      :post {:summary "Create a balance"
             :openapi {:operationId "CreateBalance"}
             :parameters {:body [:ref "CreateBalanceRequest"]}
             :responses {201 {:body [:ref "Balance"]}}
             :handler handlers/create-balance}}]
    ["/{balance-type}/{currency}/{balance-status}"
     {:get {:summary "Retrieve a balance"
            :openapi {:operationId "RetrieveBalance"}
            :parameters
            {:path
             {:balance-type [:enum
                             {:json-schema coercion/balance-type-json-schema
                              :decode/api coercion/decode-balance-type}
                             :balance-type-default
                             :balance-type-interest-accrued
                             :balance-type-interest-paid
                             :balance-type-purchase
                             :balance-type-cash]
              :currency string?
              :balance-status [:enum
                               {:json-schema coercion/balance-status-json-schema
                                :decode/api coercion/decode-balance-status}
                               :balance-status-posted
                               :balance-status-pending-incoming
                               :balance-status-pending-outgoing]}}
            :responses {200 {:body [:ref "Balance"]}
                        404 (ErrorResponse [#'BalanceNotFound])}
            :handler queries/get-balance}}]]
   ["/cash-account-products/{product-id}/versions/{version-id}/balance-products"
    {:openapi {:tags ["Balance Products"] :security [{"orgAuth" []}]}
     :parameters {:path {:product-id string? :version-id string?}}}
    [""
     {:get {:summary "Retrieve balance products for a version"
            :openapi {:operationId "RetrieveBalanceProducts"}
            :responses {200 {:body [:ref "BalanceProductList"]}}
            :handler queries/list-balance-products}}]]])
