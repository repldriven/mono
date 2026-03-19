(ns com.repldriven.mono.bank-balance.domain)

(defn new-balance
  "Creates a new Balance record map."
  [{:keys [account-id balance-type balance-status currency]}]
  (let [now (System/currentTimeMillis)]
    {:account-id account-id
     :balance-type balance-type
     :balance-status balance-status
     :currency currency
     :credit 0
     :debit 0
     :created-at now
     :updated-at now}))
