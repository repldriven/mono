(ns com.repldriven.mono.bank-api.cash-account-products.components
  (:require
    [com.repldriven.mono.bank-api.cash-account-products.examples
     :as examples]

    [com.repldriven.mono.bank-api.schema
     :refer [components-registry]]))

(def DraftCashAccountProductRequest
  [:map
   {:json-schema/example examples/DraftCashAccountProductRequest}
   [:name string?]
   [:account-type string?]
   [:balance-sheet-side string?]
   [:allowed-currencies {:optional true}
    [:maybe [:vector [:ref "Currency"]]]]
   [:balance-products {:optional true}
    [:maybe [:vector [:ref "BalanceProductRequest"]]]]
   [:valid-from {:optional true} [:maybe string?]]
   [:valid-to {:optional true} [:maybe string?]]])

(def CashAccountProductVersion
  [:map
   {:json-schema/example examples/CashAccountProductVersion}
   [:organization-id string?]
   [:product-id string?]
   [:version-id string?]
   [:version-number int?]
   [:status string?]
   [:name {:optional true} [:maybe string?]]
   [:account-type {:optional true}
    [:maybe
     [:enum :current :savings :term-deposit
      :account-type-unknown]]]
   [:balance-sheet-side {:optional true}
    [:maybe
     [:enum :asset :liability
      :balance-sheet-side-unknown]]]
   [:allowed-currencies {:optional true}
    [:maybe [:vector [:ref "Currency"]]]]
   [:balance-products {:optional true}
    [:maybe [:vector [:ref "BalanceProduct"]]]]
   [:valid-from {:optional true} [:maybe string?]]
   [:valid-to {:optional true} [:maybe string?]]
   [:created-at {:optional true} [:maybe string?]]
   [:updated-at {:optional true} [:maybe string?]]])

(def CashAccountProductVersionList
  [:map
   {:json-schema/example examples/CashAccountProductVersionList}
   [:versions [:vector [:ref "CashAccountProductVersion"]]]])

(def DraftCashAccountProductVersionRequest
  [:map
   {:json-schema/example examples/DraftCashAccountProductVersionRequest}
   [:name string?]
   [:account-type string?]
   [:balance-sheet-side string?]
   [:allowed-currencies {:optional true}
    [:maybe [:vector [:ref "Currency"]]]]
   [:balance-products {:optional true}
    [:maybe [:vector [:ref "BalanceProductRequest"]]]]
   [:valid-from {:optional true} [:maybe string?]]
   [:valid-to {:optional true} [:maybe string?]]])

(def registry
  (components-registry [#'DraftCashAccountProductRequest
                        #'CashAccountProductVersion
                        #'CashAccountProductVersionList
                        #'DraftCashAccountProductVersionRequest]))
