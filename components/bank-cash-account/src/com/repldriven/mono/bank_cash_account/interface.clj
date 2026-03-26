(ns com.repldriven.mono.bank-cash-account.interface
  (:require
    com.repldriven.mono.bank-cash-account.system

    [com.repldriven.mono.bank-cash-account.core :as core]
    [com.repldriven.mono.bank-cash-account.store :as store]))

(defn new-account
  "Opens a cash account with balances. Returns account map or
  anomaly."
  [config data]
  (core/new-account config data))

(defn get-accounts
  "Lists cash accounts for an organization. Returns
  {:accounts [maps] :before id|nil :after id|nil} or
  anomaly. opts supports :after, :before, :limit."
  ([config org-id]
   (store/get-accounts config org-id {}))
  ([config org-id opts]
   (store/get-accounts config org-id opts)))

(defn get-accounts-by-type
  "Returns accounts matching the given account-type.
  Uses secondary index."
  [config account-type]
  (store/get-accounts-by-type config account-type))
