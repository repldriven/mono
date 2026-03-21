(ns com.repldriven.mono.bank-balance.core
  (:require
    [com.repldriven.mono.bank-balance.domain :as domain]
    [com.repldriven.mono.bank-balance.store :as store]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn new-balance
  "Creates a new balance. Rejects if the balance already
  exists. Returns the balance or anomaly."
  [config data]
  (let [{:keys [account-id balance-type currency
                balance-status]}
        data]
    (let-nom>
      [existing (store/get-balance config
                                   account-id
                                   balance-type
                                   currency
                                   balance-status)
       _ (when existing
           (error/reject :balance/already-exists
                         {:message "Balance already exists"
                          :account-id account-id
                          :balance-type balance-type
                          :currency currency
                          :balance-status balance-status}))
       balance (domain/new-balance data)
       _ (store/save config balance)]
      balance)))

(defn update-balance
  "Updates an existing balance. Rejects if the balance
  does not exist. Returns the updated balance or anomaly."
  [config balance]
  (let [{:keys [account-id balance-type currency
                balance-status]}
        balance]
    (let-nom>
      [existing (store/get-balance config
                                   account-id
                                   balance-type
                                   currency
                                   balance-status)
       _ (when-not existing
           (error/reject :balance/not-found
                         {:message "Balance not found"
                          :account-id account-id
                          :balance-type balance-type
                          :currency currency
                          :balance-status balance-status}))
       _ (store/save config balance)]
      balance)))
