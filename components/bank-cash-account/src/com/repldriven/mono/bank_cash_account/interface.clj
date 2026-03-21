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
  "Lists cash accounts for an organization. Returns sequence
  of account maps or anomaly."
  [config org-id]
  (store/get-accounts config org-id))
