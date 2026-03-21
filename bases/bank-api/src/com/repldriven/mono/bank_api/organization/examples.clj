(ns com.repldriven.mono.bank-api.organization.examples
  (:require
    [com.repldriven.mono.bank-api.api-key.examples :as
     api-key-examples]
    [com.repldriven.mono.bank-api.balance.examples :as
     balance-examples]
    [com.repldriven.mono.bank-api.cash-account.examples :as
     cash-account-examples]
    [com.repldriven.mono.bank-api.party.examples :as
     party-examples]
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def registry (examples-registry []))

(def Organization
  {:organization-id "org_01JMABC123"
   :name "Acme Corp"
   :type :customer
   :status "active"
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"
   :party (assoc party-examples/Party :type :organization)
   :accounts [(assoc cash-account-examples/CashAccount
                     :balances
                     [balance-examples/Balance])]
   :api-key api-key-examples/ApiKey})

(def OrganizationList {:organizations [Organization]})

(def CreateOrganizationRequest {:name "Acme Corp" :currencies ["GBP"]})

(def CreateOrganizationResponse
  (assoc Organization :api-key-secret api-key-examples/ApiKeySecret))

