(ns com.repldriven.mono.bank-api.party.components
  (:require
    [com.repldriven.mono.bank-api.party.coercion :as coercion]
    [com.repldriven.mono.bank-api.party.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def PartyId [:string {:title "PartyId" :json-schema/example examples/PartyId}])

(def PartyType
  (coercion/party-type-enum-schema {:json-schema/example "person"}))

(def PartyStatus
  (coercion/party-status-enum-schema {:json-schema/example "active"}))

(def IdentifierType
  (coercion/identifier-type-enum-schema {:json-schema/example "passport"}))

(def Party
  [:map {:json-schema/example examples/Party}
   [:organization-id {:optional true} [:maybe string?]]
   [:party-id [:ref "PartyId"]]
   [:type [:ref "PartyType"]]
   [:display-name string?]
   [:status [:ref "PartyStatus"]]
   [:created-at {:optional true} [:maybe [:ref "Timestamp"]]]
   [:updated-at {:optional true} [:maybe [:ref "Timestamp"]]]])

(def NationalIdentifier
  [:map
   [:type [:ref "IdentifierType"]]
   [:value string?]
   [:issuing-country string?]])

(def CreatePartyRequest
  [:map {:json-schema/example examples/CreatePartyRequest}
   [:type
    [:enum
     {:json-schema coercion/party-type-json-schema
      :decode/api coercion/decode-party-type}
     :party-type-person]]
   [:display-name string?] [:given-name string?]
   [:middle-names {:optional true} [:maybe string?]]
   [:family-name string?]
   [:date-of-birth int?] [:nationality string?]
   [:national-identifier {:optional true}
    [:maybe [:ref "NationalIdentifier"]]]])

(def CreatePartyResponse [:ref "Party"])

(def PartyList
  [:map {:json-schema/example examples/PartyList}
   [:parties [:vector [:ref "Party"]]]
   [:links {:optional true}
    [:map
     [:next {:optional true} string?]
     [:prev {:optional true} string?]]]])

(def registry
  (components-registry [#'PartyId #'PartyType #'PartyStatus #'IdentifierType
                        #'Party #'NationalIdentifier #'CreatePartyRequest
                        #'CreatePartyResponse #'PartyList]))
