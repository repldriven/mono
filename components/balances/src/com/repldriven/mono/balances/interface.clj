(ns com.repldriven.mono.balances.interface
  (:require
    [com.repldriven.mono.balances.domain :as domain]
    [com.repldriven.mono.balances.store :as store]

    [com.repldriven.mono.error.interface :as error]))

(defn new-balance
  "Creates a new balance. Returns the balance map or anomaly."
  [config balance-data]
  (let [balance (domain/new-balance balance-data)]
    (error/let-nom> [_ (store/save config balance)]
      balance)))

(defn get-balance
  "Loads a balance by account-id, balance-type, currency,
  and balance-status. Returns the balance map, nil if not
  found, or anomaly."
  [config account-id balance-type currency balance-status]
  (store/load config
              account-id
              balance-type
              currency
              balance-status))

(defn get-balances
  "Lists balances for an account. Returns a sequence of
  balance maps or anomaly."
  [config account-id]
  (store/get-account-balances config account-id))
