(ns com.repldriven.mono.bank-api.balance.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def BalanceNotFound
  {:value {:title "REJECTED"
           :type "balances/not-found"
           :status 404
           :detail "Balance not found"}})

(def registry (examples-registry [#'BalanceNotFound]))

(def Balance
  {:account-id "acc_01JMABC123DEF456"
   :balance-type :default
   :balance-status :posted
   :currency "GBP"
   :credit 0
   :debit 0
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"})

(def BalanceList {:balances [Balance]})

(def CreateBalanceRequest
  {:balance-type :default :balance-status :posted :currency "GBP"})

(def BalanceProduct {:balance-type :default :balance-status :posted})

(def BalanceProductList {:balance-products [BalanceProduct]})
