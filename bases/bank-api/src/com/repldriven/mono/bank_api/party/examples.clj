(ns com.repldriven.mono.bank-api.party.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def PartyNotFound
  {:value {:title "REJECTED"
           :type "party/not-found"
           :status 404
           :detail "Party not found"}})

(def DuplicateNationalIdentifier
  {:value {:title "REJECTED"
           :type "party/duplicate-national-identifier"
           :status 422
           :detail "National identifier already exists"}})

(def registry
  (examples-registry [#'PartyNotFound #'DuplicateNationalIdentifier]))

(def Party
  {:organization-id "org_01JMABC"
   :party-id "pty_01JMABC123"
   :type :person
   :display-name "Jane Doe"
   :status :pending
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"})

(def PartyId (:party-id Party))

(def PartyList {:parties [Party]})

(def CreatePartyRequest
  {:type :person
   :display-name "Jane Doe"
   :given-name "Jane"
   :family-name "Doe"
   :date-of-birth 19900115
   :nationality "GB"
   :national-identifier
   {:type :national-insurance :value "TN000001A" :issuing-country "GBR"}})

(def CreatePartyResponse Party)
