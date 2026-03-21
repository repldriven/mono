(ns com.repldriven.mono.bank-api.cash-account.components
  (:require
    [com.repldriven.mono.bank-api.cash-account.coercion :as coercion]
    [com.repldriven.mono.bank-api.cash-account.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def CashAccountId
  [:string
   {:title "CashAccountId" :json-schema/example examples/CashAccountId}])

(def ScanAddress
  [:map
   [:sort-code string?]
   [:account-number string?]])

(def PaymentAddress
  [:map [:scheme string?]
   [:identifier {:optional true}
    [:maybe
     [:map
      [:scan {:optional true} [:maybe [:ref "ScanAddress"]]]
      [:value {:optional true} [:maybe string?]]]]]])

(def CashAccountStatus
  (coercion/cash-account-status-enum-schema {:json-schema/example "opened"}))

(def CashAccount
  [:map {:json-schema/example examples/CashAccount}
   [:organization-id {:optional true} [:maybe string?]]
   [:account-id [:ref "CashAccountId"]]
   [:party-id string?]
   [:name string?]
   [:currency [:ref "Currency"]]
   [:product-id string?]
   [:version-id string?]
   [:account-status [:ref "CashAccountStatus"]]
   [:payment-addresses {:optional true}
    [:maybe [:vector [:ref "PaymentAddress"]]]]
   [:balances {:optional true} [:maybe [:vector [:ref "Balance"]]]]
   [:created-at {:optional true} [:maybe [:ref "Timestamp"]]]
   [:updated-at {:optional true} [:maybe [:ref "Timestamp"]]]])

(def CreateCashAccountRequest
  [:map {:json-schema/example examples/CreateCashAccountRequest}
   [:party-id string?]
   [:name string?]
   [:currency [:ref "Currency"]]
   [:product-id string?]])

(def CreateCashAccountResponse [:ref "CashAccount"])

(def CashAccountList
  [:map {:json-schema/example examples/CashAccountList}
   [:cash-accounts [:vector [:ref "CashAccount"]]]
   [:links {:optional true}
    [:map
     [:next {:optional true} string?]
     [:prev {:optional true} string?]]]])

(def CloseCashAccountResponse [:ref "CashAccount"])

(def registry
  (components-registry [#'CashAccountId #'ScanAddress #'PaymentAddress
                        #'CashAccountStatus #'CashAccount
                        #'CreateCashAccountRequest #'CreateCashAccountResponse
                        #'CashAccountList #'CloseCashAccountResponse]))
