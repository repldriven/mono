(ns com.repldriven.mono.organizations.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(def ^:private api-key-prefix "sk_live_")
(def ^:private api-key-display-prefix-len 12)

(defn new-organization
  "Creates a new Organization record map."
  [org-name]
  (let [now (System/currentTimeMillis)]
    {:organization-id (encryption/generate-id "org")
     :name org-name
     :status "active"
     :created-at now
     :updated-at now}))

(defn generate-api-key
  "Generates an API key and returns {:raw-key ... :key-hash
  ... :key-prefix ...}."
  []
  (let [raw-key (encryption/generate-api-key api-key-prefix)
        key-hash (encryption/hash-api-key raw-key)
        key-prefix
        (subs raw-key 0 (min api-key-display-prefix-len (count raw-key)))]
    {:raw-key raw-key :key-hash key-hash :key-prefix key-prefix}))

(defn new-api-key
  "Creates a new ApiKey record map (without the raw key)."
  [org-id api-key-name key-data]
  (let [now (System/currentTimeMillis)]
    {:id (encryption/generate-id "sk")
     :organization-id org-id
     :key-hash (:key-hash key-data)
     :key-prefix (:key-prefix key-data)
     :name api-key-name
     :created-at now}))

(defn hash-raw-key
  "Hashes a raw API key string."
  [raw-key]
  (encryption/hash-api-key raw-key))
