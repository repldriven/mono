(ns com.repldriven.mono.bank-api.cash-account-product.components
  (:require
    [com.repldriven.mono.bank-api.cash-account-product.coercion :as
     coercion]
    [com.repldriven.mono.bank-api.cash-account-product.examples :as
     examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def DraftCashAccountProductRequest
  [:map {:json-schema/example examples/DraftCashAccountProductRequest}
   [:name string?]
   [:account-type
    [:enum
     {:json-schema coercion/account-type-json-schema
      :decode/api coercion/decode-account-type}
     :account-type-current :account-type-savings
     :account-type-term-deposit]]
   [:balance-sheet-side
    [:enum
     {:json-schema coercion/balance-sheet-side-json-schema
      :decode/api coercion/decode-balance-sheet-side}
     :balance-sheet-side-asset :balance-sheet-side-liability]]
   [:allowed-currencies {:optional true} [:maybe [:vector [:ref "Currency"]]]]
   [:balance-products {:optional true}
    [:maybe [:vector [:ref "BalanceProductRequest"]]]]
   [:valid-from {:optional true} [:maybe string?]]
   [:valid-to {:optional true} [:maybe string?]]])

(def CashAccountProductVersion
  [:map {:json-schema/example examples/CashAccountProductVersion}
   [:organization-id string?] [:product-id string?] [:version-id string?]
   [:version-number int?] [:status string?]
   [:name {:optional true} [:maybe string?]]
   [:account-type {:optional true}
    [:maybe
     [:enum
      {:json-schema coercion/account-type-json-schema
       :decode/api coercion/decode-account-type
       :encode/api coercion/encode-account-type}
      :account-type-current :account-type-savings
      :account-type-term-deposit :account-type-unknown]]]
   [:balance-sheet-side {:optional true}
    [:maybe
     [:enum
      {:json-schema coercion/balance-sheet-side-json-schema
       :decode/api coercion/decode-balance-sheet-side
       :encode/api coercion/encode-balance-sheet-side}
      :balance-sheet-side-asset :balance-sheet-side-liability
      :balance-sheet-side-unknown]]]
   [:allowed-currencies {:optional true} [:maybe [:vector [:ref "Currency"]]]]
   [:balance-products {:optional true}
    [:maybe [:vector [:ref "BalanceProduct"]]]]
   [:valid-from {:optional true} [:maybe string?]]
   [:valid-to {:optional true} [:maybe string?]]
   [:created-at {:optional true} [:maybe string?]]
   [:updated-at {:optional true} [:maybe string?]]])

(def CashAccountProductVersionList
  [:map {:json-schema/example examples/CashAccountProductVersionList}
   [:versions [:vector [:ref "CashAccountProductVersion"]]]])

(def DraftCashAccountProductVersionRequest
  [:map {:json-schema/example examples/DraftCashAccountProductVersionRequest}
   [:name string?]
   [:account-type
    [:enum
     {:json-schema coercion/account-type-json-schema
      :decode/api coercion/decode-account-type}
     :account-type-current :account-type-savings
     :account-type-term-deposit]]
   [:balance-sheet-side
    [:enum
     {:json-schema coercion/balance-sheet-side-json-schema
      :decode/api coercion/decode-balance-sheet-side}
     :balance-sheet-side-asset :balance-sheet-side-liability]]
   [:allowed-currencies {:optional true} [:maybe [:vector [:ref "Currency"]]]]
   [:balance-products {:optional true}
    [:maybe [:vector [:ref "BalanceProductRequest"]]]]
   [:valid-from {:optional true} [:maybe string?]]
   [:valid-to {:optional true} [:maybe string?]]])

(def registry
  (components-registry
   [#'DraftCashAccountProductRequest #'CashAccountProductVersion
    #'CashAccountProductVersionList #'DraftCashAccountProductVersionRequest]))
