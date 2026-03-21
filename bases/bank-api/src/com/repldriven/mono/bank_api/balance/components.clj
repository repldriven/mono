(ns com.repldriven.mono.bank-api.balance.components
  (:require
    [com.repldriven.mono.bank-api.balance.coercion :as coercion]
    [com.repldriven.mono.bank-api.balance.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def BalanceType
  (coercion/balance-type-enum-schema {:json-schema/example "default"}))

(def BalanceStatus
  (coercion/balance-status-enum-schema {:json-schema/example "posted"}))

(def Balance
  [:map {:json-schema/example examples/Balance}
   [:account-id string?]
   [:balance-type [:ref "BalanceType"]]
   [:balance-status [:ref "BalanceStatus"]]
   [:currency [:ref "Currency"]]
   [:credit int?]
   [:debit int?]
   [:created-at {:optional true} [:maybe [:ref "Timestamp"]]]
   [:updated-at {:optional true} [:maybe [:ref "Timestamp"]]]])

(def BalanceList
  [:map {:json-schema/example examples/BalanceList}
   [:balances [:vector [:ref "Balance"]]]])

(def CreateBalanceRequest
  [:map {:json-schema/example examples/CreateBalanceRequest}
   [:balance-type [:ref "BalanceType"]]
   [:balance-status [:ref "BalanceStatus"]]
   [:currency [:ref "Currency"]]])

(def CreateBalanceResponse [:ref "Balance"])

(def BalanceProduct
  [:map {:json-schema/example examples/BalanceProduct}
   [:balance-type [:ref "BalanceType"]]
   [:balance-status [:ref "BalanceStatus"]]])

(def BalanceProductList
  [:map {:json-schema/example examples/BalanceProductList}
   [:balance-products [:vector [:ref "BalanceProduct"]]]])

(def BalanceProductRequest
  [:map {:json-schema/example examples/BalanceProduct}
   [:balance-type [:ref "BalanceType"]]
   [:balance-status [:ref "BalanceStatus"]]])

(def registry
  (components-registry [#'BalanceType #'BalanceStatus #'Balance #'BalanceList
                        #'CreateBalanceRequest #'CreateBalanceResponse
                        #'BalanceProduct #'BalanceProductList
                        #'BalanceProductRequest]))
