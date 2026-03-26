(ns com.repldriven.mono.bank-balance.interface
  (:require
    [com.repldriven.mono.bank-balance.core :as core]
    [com.repldriven.mono.bank-balance.store :as store]))

(defn new-balance
  "Creates a new balance. Rejects if the balance already
  exists. Returns the balance or anomaly."
  [config data]
  (core/new-balance config data))

(defn update-balance
  "Updates an existing balance. Rejects if the balance
  does not exist. Returns the updated balance or anomaly."
  [config balance]
  (core/update-balance config balance))

(defn get-balance
  "Loads a balance by account-id, balance-type, currency,
  and balance-status. Returns the balance, nil if not
  found, or anomaly."
  [config account-id balance-type currency balance-status]
  (store/get-balance config
                     account-id
                     balance-type
                     currency
                     balance-status))

(defn get-balances
  "Lists balances for an account. Returns a sequence of
  balances or anomaly."
  [config account-id]
  (store/get-account-balances config account-id))

(defn apply-legs
  "Applies all legs to balances. For use inside an open
  store."
  [store legs]
  (store/apply-legs store legs))
