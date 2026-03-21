(ns com.repldriven.mono.bank-party.domain
  (:refer-clojure :exclude [type])
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(defn new-party
  "Creates a new party map. Person parties start pending;
  internal and organization parties start active."
  [data]
  (let [{:keys [organization-id type display-name]} data
        now (System/currentTimeMillis)
        status (if (= :party-type-person type)
                 :party-status-pending
                 :party-status-active)]
    {:organization-id organization-id
     :party-id (encryption/generate-id "pty")
     :type type
     :display-name display-name
     :status status
     :created-at now
     :updated-at now}))

(defn activate-party
  "Returns party with status active."
  [party]
  (assoc party
         :status :party-status-active
         :updated-at (System/currentTimeMillis)))

(defn new-party-national-identifier
  "Creates a party-national-identifier map linked to
  organization-id and party-id."
  [national-identifier organization-id party-id]
  (let [{:keys [type value issuing-country]} national-identifier]
    {:organization-id organization-id
     :party-id party-id
     :type type
     :value value
     :issuing-country issuing-country
     :created-at (System/currentTimeMillis)}))

(defn new-person-identification
  "Creates a person-identification map linked to party-id."
  [data party-id]
  (let [{:keys [given-name middle-names family-name
                date-of-birth nationality]}
        data
        now (System/currentTimeMillis)]
    (cond-> {:party-id party-id
             :given-name given-name
             :family-name family-name
             :date-of-birth date-of-birth
             :nationality nationality
             :created-at now
             :updated-at now}
            middle-names
            (assoc :middle-names middle-names))))
