(ns com.repldriven.mono.bank-api-key.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(def ^:private api-key-prefix "sk_live_")
(def ^:private api-key-display-prefix-len 12)

(defn new-api-key
  "Creates a new ApiKey record map and its key secret.
  Returns {:api-key <map> :key-secret <string>}. The
  key-secret is only available at creation time."
  [org-id key-name]
  (let [key-secret (encryption/generate-token api-key-prefix)
        key-hash (encryption/hash-token key-secret)
        key-prefix
        (subs key-secret 0 (min api-key-display-prefix-len (count key-secret)))
        now (System/currentTimeMillis)]
    {:api-key {:id (encryption/generate-id "sk")
               :organization-id org-id
               :key-hash key-hash
               :key-prefix key-prefix
               :name key-name
               :created-at now}
     :key-secret key-secret}))
