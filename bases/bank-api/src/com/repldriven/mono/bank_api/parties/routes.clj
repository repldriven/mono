(ns com.repldriven.mono.bank-api.parties.routes
  (:require
    [com.repldriven.mono.bank-api.parties.commands :as commands]
    [com.repldriven.mono.bank-api.parties.queries :as queries]
    [com.repldriven.mono.bank-api.parties.examples
     :refer [DuplicateNationalIdentifier PartyNotFound]]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]

    [com.repldriven.mono.telemetry.interface :as telemetry]))

(def ^:private list-parties-query-schema
  [:map [(keyword "page[after]") {:optional true} string?]
   [(keyword "page[before]") {:optional true} string?]
   [(keyword "page[size]") {:optional true} string?]])

(def routes
  [["/parties"
    {:openapi {:tags ["Parties"] :security [{"orgAuth" []}]}}
    [""
     {:get {:summary "List parties"
            :openapi {:operationId "ListParties"}
            :parameters {:query list-parties-query-schema}
            :responses {200 {:body [:ref "PartyList"]}}
            :handler queries/list-parties}
      :post {:summary "Create a party"
             :openapi {:operationId "CreateParty"}
             :interceptors [telemetry/require-idempotency-key]
             :parameters {:body [:ref "CreatePartyRequest"]}
             :responses {200 {:body [:ref "CreatePartyResponse"]}
                         422 (ErrorResponse [#'DuplicateNationalIdentifier])}
             :handler commands/create-party}}]
    ["/{party-id}"
     {:parameters {:path {:party-id [:ref "PartyId"]}}}
     [""
      {:get {:summary "Get a party"
             :openapi {:operationId "RetrieveParty"}
             :responses {200 {:body [:ref "Party"]}
                         404 (ErrorResponse [#'PartyNotFound])}
             :handler queries/get-party}}]]]])
