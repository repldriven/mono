(ns com.repldriven.mono.bank-schema.interface
  (:require
    [com.repldriven.mono.schemas.balances :as balances]
    [com.repldriven.mono.schemas.cash_account_products :as
     cash-account-products]
    [com.repldriven.mono.schemas.cash_accounts :as cash-accounts]
    [com.repldriven.mono.schemas.idv :as idv]
    [com.repldriven.mono.schemas.keys :as keys]
    [com.repldriven.mono.schemas.organizations :as organizations]
    [com.repldriven.mono.schemas.party :as party]
    [com.repldriven.mono.schemas.person_identification :as
     person-identification]
    [com.repldriven.mono.schemas.transactions :as transactions]
    [protojure.protobuf :as proto])
  (:import
    (com.repldriven.mono.schemas.balances BalanceProto$Balance)
    (com.repldriven.mono.schemas.cash_account_products
     CashAccountProductProto$CashAccountProductVersion)
    (com.repldriven.mono.schemas.cash_accounts
     CashAccountProto$CashAccount
     CashAccountChangelogProto$CashAccountChangelog)
    (com.repldriven.mono.schemas.idv IdvProto$Idv
                                     IdvChangelogProto$IdvChangelog)
    (com.repldriven.mono.schemas.keys ApiKeyProto$ApiKey)
    (com.repldriven.mono.schemas.organizations
     OrganizationProto$Organization
     OrganizationChangelogProto$OrganizationChangelog)
    (com.repldriven.mono.schemas.party
     PartyProto$Party
     PartyChangelogProto$PartyChangelog
     PartyNationalIdentifierProto$PartyNationalIdentifier)
    (com.repldriven.mono.schemas.person_identification
     PersonIdentificationProto$PersonIdentification)
    (com.repldriven.mono.schemas.transactions
     TransactionProto$Transaction
     TransactionProto$TransactionLeg)))

(def pb->Balance balances/pb->Balance)
(defn Balance->pb [m] (proto/->pb (balances/new-Balance m)))
(defn Balance->java [m] (BalanceProto$Balance/parseFrom (Balance->pb m)))

(def balance-type->int balances/Balance-BalanceType-label2val)
(def balance-status->int balances/Balance-BalanceStatus-label2val)

(def pb->CashAccountProductVersion
  cash-account-products/pb->CashAccountProductVersion)
(defn CashAccountProductVersion->pb
  [m]
  (proto/->pb (cash-account-products/new-CashAccountProductVersion m)))
(defn CashAccountProductVersion->java
  [m]
  (CashAccountProductProto$CashAccountProductVersion/parseFrom
   (CashAccountProductVersion->pb m)))

(def pb->ApiKey keys/pb->ApiKey)
(defn ApiKey->pb [m] (proto/->pb (keys/new-ApiKey m)))
(defn ApiKey->java [m] (ApiKeyProto$ApiKey/parseFrom (ApiKey->pb m)))

(def pb->Organization organizations/pb->Organization)
(defn Organization->pb [m] (proto/->pb (organizations/new-Organization m)))
(defn Organization->java
  [m]
  (OrganizationProto$Organization/parseFrom (Organization->pb m)))

(def pb->Party party/pb->Party)
(defn Party->pb [m] (proto/->pb (party/new-Party m)))
(defn Party->java [m] (PartyProto$Party/parseFrom (Party->pb m)))

(def pb->PartyNationalIdentifier party/pb->PartyNationalIdentifier)
(defn PartyNationalIdentifier->pb
  [m]
  (proto/->pb (party/new-PartyNationalIdentifier m)))
(defn PartyNationalIdentifier->java
  [m]
  (PartyNationalIdentifierProto$PartyNationalIdentifier/parseFrom
   (PartyNationalIdentifier->pb m)))

(def pb->PersonIdentification person-identification/pb->PersonIdentification)
(defn PersonIdentification->pb
  [m]
  (proto/->pb (person-identification/new-PersonIdentification m)))
(defn PersonIdentification->java
  [m]
  (PersonIdentificationProto$PersonIdentification/parseFrom
   (PersonIdentification->pb m)))

(def pb->Idv idv/pb->Idv)
(defn Idv->pb [m] (proto/->pb (idv/new-Idv m)))
(defn Idv->java [m] (IdvProto$Idv/parseFrom (Idv->pb m)))

(def pb->CashAccount cash-accounts/pb->CashAccount)
(defn CashAccount->pb [m] (proto/->pb (cash-accounts/new-CashAccount m)))
(defn CashAccount->java
  [m]
  (CashAccountProto$CashAccount/parseFrom (CashAccount->pb m)))

;; Changelog bridges

(def pb->CashAccountChangelog cash-accounts/pb->CashAccountChangelog)
(defn CashAccountChangelog->pb
  [m]
  (proto/->pb (cash-accounts/new-CashAccountChangelog m)))
(defn CashAccountChangelog->java
  [m]
  (CashAccountChangelogProto$CashAccountChangelog/parseFrom
   (CashAccountChangelog->pb m)))

(def pb->PartyChangelog party/pb->PartyChangelog)
(defn PartyChangelog->pb [m] (proto/->pb (party/new-PartyChangelog m)))
(defn PartyChangelog->java
  [m]
  (PartyChangelogProto$PartyChangelog/parseFrom (PartyChangelog->pb m)))

(def pb->IdvChangelog idv/pb->IdvChangelog)
(defn IdvChangelog->pb [m] (proto/->pb (idv/new-IdvChangelog m)))
(defn IdvChangelog->java
  [m]
  (IdvChangelogProto$IdvChangelog/parseFrom (IdvChangelog->pb m)))

(def pb->Transaction transactions/pb->Transaction)
(defn Transaction->pb [m] (proto/->pb (transactions/new-Transaction m)))
(defn Transaction->java
  [m]
  (TransactionProto$Transaction/parseFrom (Transaction->pb m)))

(def pb->TransactionLeg transactions/pb->TransactionLeg)
(defn TransactionLeg->pb [m] (proto/->pb (transactions/new-TransactionLeg m)))
(defn TransactionLeg->java
  [m]
  (TransactionProto$TransactionLeg/parseFrom (TransactionLeg->pb m)))

(def pb->OrganizationChangelog organizations/pb->OrganizationChangelog)
(defn OrganizationChangelog->pb
  [m]
  (proto/->pb (organizations/new-OrganizationChangelog m)))
(defn OrganizationChangelog->java
  [m]
  (OrganizationChangelogProto$OrganizationChangelog/parseFrom
   (OrganizationChangelog->pb m)))
