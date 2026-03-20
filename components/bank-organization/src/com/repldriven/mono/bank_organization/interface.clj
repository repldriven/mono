(ns com.repldriven.mono.bank-organization.interface
  (:require
    [com.repldriven.mono.bank-organization.core :as core]))

(defn new-organization
  "Creates an organization with API key, internal party,
  product, and one cash account per currency. Returns map
  or anomaly."
  [config org-name org-type currencies]
  (core/new-organization config org-name org-type currencies))

(defn get-organization
  "Enriches a flat organization map with party, accounts
  (with balances), and api-key. Returns rich organization
  map or anomaly."
  [config org]
  (core/get-organization config org))

(defn get-organizations
  "Lists organizations enriched with party, accounts, and
  api-key. Returns a sequence of rich organization maps or
  anomaly."
  [config]
  (core/get-organizations config))
