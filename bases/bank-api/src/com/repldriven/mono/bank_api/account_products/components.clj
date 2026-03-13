(ns com.repldriven.mono.bank-api.account-products.components
  (:require
    [com.repldriven.mono.bank-api.account-products.examples
     :as examples]

    [com.repldriven.mono.bank-api.schema
     :refer [components-registry]]))

(def DraftAccountProductRequest
  [:map
   {:json-schema/example examples/DraftAccountProductRequest}
   [:name string?]
   [:account-type string?]
   [:balance-sheet-side string?]
   [:allowed-currencies {:optional true}
    [:maybe [:vector [:ref "Currency"]]]]
   [:valid-from {:optional true} [:maybe string?]]
   [:valid-to {:optional true} [:maybe string?]]])

(def AccountProductVersion
  [:map
   {:json-schema/example examples/AccountProductVersion}
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
   [:valid-from {:optional true} [:maybe string?]]
   [:valid-to {:optional true} [:maybe string?]]
   [:created-at {:optional true} [:maybe string?]]
   [:updated-at {:optional true} [:maybe string?]]])

(def AccountProductVersionList
  [:map
   {:json-schema/example examples/AccountProductVersionList}
   [:versions [:vector [:ref "AccountProductVersion"]]]])

(def DraftAccountProductVersionRequest
  [:map
   {:json-schema/example examples/DraftAccountProductVersionRequest}
   [:name string?]
   [:account-type string?]
   [:balance-sheet-side string?]
   [:allowed-currencies {:optional true}
    [:maybe [:vector [:ref "Currency"]]]]
   [:valid-from {:optional true} [:maybe string?]]
   [:valid-to {:optional true} [:maybe string?]]])

(def registry
  (components-registry [#'DraftAccountProductRequest #'AccountProductVersion
                        #'AccountProductVersionList
                        #'DraftAccountProductVersionRequest]))
