(ns com.repldriven.mono.bank-api.party.components
  (:require
    [com.repldriven.mono.bank-api.party.coercion :as coercion]
    [com.repldriven.mono.bank-api.party.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def PartyId [:string {:title "PartyId" :json-schema/example examples/PartyId}])

(def Party
  [:map {:json-schema/example examples/Party}
   [:organization-id {:optional true} [:maybe string?]]
   [:party-id [:ref "PartyId"]]
   [:type
    [:enum
     {:json-schema coercion/party-type-json-schema
      :decode/api coercion/decode-party-type
      :encode/api coercion/encode-party-type}
     :party-type-person :party-type-internal
     :party-type-organization :party-type-unknown]]
   [:display-name string?]
   [:status
    [:enum
     {:json-schema coercion/party-status-json-schema
      :decode/api coercion/decode-party-status
      :encode/api coercion/encode-party-status}
     :party-status-pending :party-status-active
     :party-status-suspended :party-status-closed
     :party-status-unknown]]
   [:created-at {:optional true} [:maybe string?]]
   [:updated-at {:optional true} [:maybe string?]]])

(def NationalIdentifier
  [:map
   [:type
    [:enum
     {:json-schema coercion/identifier-type-json-schema
      :decode/api coercion/decode-identifier-type
      :encode/api coercion/encode-identifier-type}
     :identifier-type-national-insurance
     :identifier-type-passport
     :identifier-type-driving-licence
     :identifier-type-national-id-card
     :identifier-type-tax-id]]
   [:value string?]
   [:issuing-country string?]])

(def CreatePartyRequest
  [:map {:json-schema/example examples/CreatePartyRequest}
   [:type
    [:enum
     {:json-schema coercion/party-type-json-schema
      :decode/api coercion/decode-party-type}
     :party-type-person :party-type-internal]]
   [:display-name string?] [:given-name string?]
   [:middle-names {:optional true} [:maybe string?]] [:family-name string?]
   [:date-of-birth int?] [:nationality string?]
   [:national-identifier {:optional true}
    [:maybe [:ref "NationalIdentifier"]]]])

(def CreatePartyResponse [:ref "Party"])

(def PartyList
  [:map {:json-schema/example examples/PartyList}
   [:parties [:vector [:ref "Party"]]]
   [:links {:optional true}
    [:map [:next {:optional true} string?] [:prev {:optional true} string?]]]])

(def registry
  (components-registry [#'PartyId #'Party #'NationalIdentifier
                        #'CreatePartyRequest #'CreatePartyResponse
                        #'PartyList]))
