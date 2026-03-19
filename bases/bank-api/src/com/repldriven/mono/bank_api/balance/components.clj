(ns com.repldriven.mono.bank-api.balance.components
  (:require
    [com.repldriven.mono.bank-api.balance.coercion :as coercion]
    [com.repldriven.mono.bank-api.balance.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def BalanceType
  [:enum
   {:title "BalanceType"
    :json-schema/example "default"
    :json-schema coercion/balance-type-json-schema
    :decode/api coercion/decode-balance-type
    :encode/api coercion/encode-balance-type}
   :balance-type-unknown :balance-type-default
   :balance-type-interest-accrued :balance-type-interest-paid
   :balance-type-purchase :balance-type-cash])

(def BalanceStatus
  [:enum
   {:title "BalanceStatus"
    :json-schema/example "posted"
    :json-schema coercion/balance-status-json-schema
    :decode/api coercion/decode-balance-status
    :encode/api coercion/encode-balance-status}
   :balance-status-unknown :balance-status-posted
   :balance-status-pending-incoming :balance-status-pending-outgoing])

(def Balance
  [:map {:json-schema/example examples/Balance} [:account-id string?]
   [:balance-type [:ref "BalanceType"]] [:balance-status [:ref "BalanceStatus"]]
   [:currency [:ref "Currency"]] [:credit int?] [:debit int?]
   [:created-at {:optional true} [:maybe string?]]
   [:updated-at {:optional true} [:maybe string?]]])

(def BalanceList
  [:map {:json-schema/example examples/BalanceList}
   [:balances [:vector [:ref "Balance"]]]])

(def CreateBalanceRequest
  [:map {:json-schema/example examples/CreateBalanceRequest}
   [:balance-type
    [:enum
     {:json-schema coercion/balance-type-json-schema
      :decode/api coercion/decode-balance-type}
     :balance-type-default :balance-type-interest-accrued
     :balance-type-interest-paid :balance-type-purchase
     :balance-type-cash]]
   [:balance-status
    [:enum
     {:json-schema coercion/balance-status-json-schema
      :decode/api coercion/decode-balance-status}
     :balance-status-posted
     :balance-status-pending-incoming
     :balance-status-pending-outgoing]]
   [:currency [:ref "Currency"]]])

(def BalanceProduct
  [:map {:json-schema/example examples/BalanceProduct}
   [:balance-type [:ref "BalanceType"]]
   [:balance-status [:ref "BalanceStatus"]]])

(def BalanceProductRequest
  [:map {:json-schema/example examples/BalanceProduct}
   [:balance-type
    [:enum
     {:json-schema coercion/balance-type-json-schema
      :decode/api coercion/decode-balance-type}
     :balance-type-default :balance-type-interest-accrued
     :balance-type-interest-paid :balance-type-purchase
     :balance-type-cash]]
   [:balance-status
    [:enum
     {:json-schema coercion/balance-status-json-schema
      :decode/api coercion/decode-balance-status}
     :balance-status-posted
     :balance-status-pending-incoming
     :balance-status-pending-outgoing]]])

(def BalanceProductList
  [:map {:json-schema/example examples/BalanceProductList}
   [:balance-products [:vector [:ref "BalanceProduct"]]]])

(def registry
  (components-registry [#'BalanceType #'BalanceStatus #'Balance #'BalanceList
                        #'CreateBalanceRequest #'BalanceProduct
                        #'BalanceProductRequest #'BalanceProductList]))
