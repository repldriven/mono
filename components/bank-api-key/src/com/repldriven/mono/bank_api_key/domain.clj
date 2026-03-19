(ns com.repldriven.mono.bank-api-key.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(def ^:private api-key-prefix "sk_live_")
(def ^:private api-key-display-prefix-len 12)

(defn new-api-key
  "Creates a new ApiKey record map and its raw key.
  Returns {:api-key <map> :raw-key <string>}. The raw-key
  is only available at creation time."
  [org-id key-name]
  (let [raw-key (encryption/generate-token api-key-prefix)
        key-hash (encryption/hash-token raw-key)
        key-prefix
        (subs raw-key 0 (min api-key-display-prefix-len (count raw-key)))
        now (System/currentTimeMillis)]
    {:api-key {:id (encryption/generate-id "sk")
               :organization-id org-id
               :key-hash key-hash
               :key-prefix key-prefix
               :name key-name
               :created-at now}
     :raw-key raw-key}))
