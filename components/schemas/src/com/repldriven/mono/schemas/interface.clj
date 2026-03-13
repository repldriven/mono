(ns com.repldriven.mono.schemas.interface
  (:require
    [com.repldriven.mono.schemas.account_products :as account-products]
    [com.repldriven.mono.schemas.accounts :as accounts]
    [com.repldriven.mono.schemas.idv :as idv]
    [com.repldriven.mono.schemas.keys :as keys]
    [com.repldriven.mono.schemas.organizations :as organizations]
    [com.repldriven.mono.schemas.party :as party]
    [com.repldriven.mono.schemas.person_identification
     :as person-identification]
    [com.repldriven.mono.schemas.persons :as persons]

    [protojure.protobuf :as proto])
  (:import
    (com.repldriven.mono.schemas.account_products
     AccountProductProto$AccountProductVersion)
    (com.repldriven.mono.schemas.accounts
     AccountProto$Account
     AccountChangelogProto$AccountChangelog)
    (com.repldriven.mono.schemas.idv
     IdvProto$Idv
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
    (com.repldriven.mono.schemas.persons PersonProto$Person)))

(def pb->AccountProductVersion account-products/pb->AccountProductVersion)
(defn AccountProductVersion->pb
  [m]
  (proto/->pb (account-products/new-AccountProductVersion m)))
(defn AccountProductVersion->java
  [m]
  (AccountProductProto$AccountProductVersion/parseFrom
   (AccountProductVersion->pb m)))

(def pb->Person persons/pb->Person)
(defn Person->pb [m] (proto/->pb (persons/new-Person m)))
(defn Person->java [m] (PersonProto$Person/parseFrom (Person->pb m)))

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
(defn Party->java
  [m]
  (PartyProto$Party/parseFrom (Party->pb m)))

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
(defn Idv->java
  [m]
  (IdvProto$Idv/parseFrom (Idv->pb m)))

(def pb->Account accounts/pb->Account)
(defn Account->pb [m] (proto/->pb (accounts/new-Account m)))
(defn Account->java [m] (AccountProto$Account/parseFrom (Account->pb m)))

;; Changelog bridges

(def pb->AccountChangelog accounts/pb->AccountChangelog)
(defn AccountChangelog->pb
  [m]
  (proto/->pb (accounts/new-AccountChangelog m)))
(defn AccountChangelog->java
  [m]
  (AccountChangelogProto$AccountChangelog/parseFrom
   (AccountChangelog->pb m)))

(def pb->PartyChangelog party/pb->PartyChangelog)
(defn PartyChangelog->pb
  [m]
  (proto/->pb (party/new-PartyChangelog m)))
(defn PartyChangelog->java
  [m]
  (PartyChangelogProto$PartyChangelog/parseFrom
   (PartyChangelog->pb m)))

(def pb->IdvChangelog idv/pb->IdvChangelog)
(defn IdvChangelog->pb
  [m]
  (proto/->pb (idv/new-IdvChangelog m)))
(defn IdvChangelog->java
  [m]
  (IdvChangelogProto$IdvChangelog/parseFrom
   (IdvChangelog->pb m)))

(def pb->OrganizationChangelog organizations/pb->OrganizationChangelog)
(defn OrganizationChangelog->pb
  [m]
  (proto/->pb (organizations/new-OrganizationChangelog m)))
(defn OrganizationChangelog->java
  [m]
  (OrganizationChangelogProto$OrganizationChangelog/parseFrom
   (OrganizationChangelog->pb m)))
