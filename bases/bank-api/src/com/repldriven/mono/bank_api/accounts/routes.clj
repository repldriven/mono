(ns com.repldriven.mono.bank-api.accounts.routes
  (:require
    [com.repldriven.mono.bank-api.accounts.commands :as commands]
    [com.repldriven.mono.bank-api.accounts.queries :as queries]
    [com.repldriven.mono.bank-api.accounts.examples :refer
     [AccountNotFound AccountAlreadyExists ProductNotPublished
      InvalidCurrency]]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]

    [com.repldriven.mono.telemetry.interface :as telemetry]))

(def ^:private list-accounts-query-schema
  [:map [(keyword "page[after]") {:optional true} string?]
   [(keyword "page[before]") {:optional true} string?]
   [(keyword "page[size]") {:optional true} string?]])

(def routes
  [["/accounts"
    {:openapi {:tags ["Accounts"] :security [{"orgAuth" []}]}}
    [""
     {:get {:summary "List accounts"
            :openapi {:operationId "ListAccounts"}
            :parameters {:query list-accounts-query-schema}
            :responses {200 {:body [:ref "AccountList"]}}
            :handler queries/list-accounts}
      :post {:summary "Open a new account"
             :openapi {:operationId "OpenAccount"}
             :interceptors [telemetry/require-idempotency-key]
             :parameters {:body [:ref "CreateAccountRequest"]}
             :responses {200 {:body [:ref "CreateAccountResponse"]}
                         422 (ErrorResponse [#'AccountAlreadyExists
                                             #'ProductNotPublished
                                             #'InvalidCurrency])}
             :handler commands/open-account}}]
    ["/{account-id}"
     {:parameters {:path {:account-id [:ref "AccountId"]}}}
     [""
      {:get {:summary "Get an account"
             :openapi {:operationId "RetrieveAccount"}
             :responses {200 {:body [:ref "Account"]}
                         404 (ErrorResponse [#'AccountNotFound])}
             :handler queries/get-account}}]
     ["/close"
      {:post {:summary "Close an account"
              :openapi {:operationId "CloseAccount"}
              :interceptors [telemetry/require-idempotency-key]
              :responses {200 {:body [:ref "CloseAccountResponse"]}
                          404 (ErrorResponse [#'AccountNotFound])}
              :handler commands/close-account}}]]]])
