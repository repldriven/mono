(ns com.repldriven.mono.bank-api-key.interface
  (:require
    [com.repldriven.mono.bank-api-key.domain :as domain]
    [com.repldriven.mono.bank-api-key.store :as store]))

(defn new-api-key
  "Creates a new ApiKey record map and its key secret.
  Returns {:api-key <map> :key-secret <string>}. The
  key-secret is only available at creation time."
  [org-id key-name]
  (domain/new-api-key org-id key-name))

(defn get-api-key
  "Looks up an API key by its hash. Returns the ApiKey map
  or nil."
  [config key-hash]
  (store/get-api-key config key-hash))

(defn get-api-keys
  "Lists all API keys for a given organization. Returns a
  sequence of ApiKey maps."
  [config org-id]
  (store/get-api-keys config org-id))
