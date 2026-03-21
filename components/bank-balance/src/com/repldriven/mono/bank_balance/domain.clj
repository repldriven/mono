(ns com.repldriven.mono.bank-balance.domain)

(defn new-balance
  "Creates a new Balance record map. Credit and debit
  default to zero if not provided."
  [{:keys [account-id balance-type balance-status currency
           credit debit]}]
  (let [now (System/currentTimeMillis)]
    {:account-id account-id
     :balance-type balance-type
     :balance-status balance-status
     :currency currency
     :credit (or credit 0)
     :debit (or debit 0)
     :created-at now
     :updated-at now}))
