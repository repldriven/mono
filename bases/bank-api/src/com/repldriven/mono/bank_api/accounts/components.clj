(ns com.repldriven.mono.bank-api.accounts.components
  (:require
    [com.repldriven.mono.bank-api.accounts.examples :as examples]

    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def AccountId
  [:string {:title "AccountId" :json-schema/example examples/AccountId}])

(def ScanAddress
  [:map
   [:sort-code string?]
   [:account-number string?]])

(def PaymentAddress
  [:map
   [:scheme string?]
   [:identifier {:optional true}
    [:maybe
     [:map
      [:scan {:optional true}
       [:maybe [:ref "ScanAddress"]]]
      [:value {:optional true}
       [:maybe string?]]]]]])

(def Account
  [:map
   {:json-schema/example examples/Account}
   [:organization-id {:optional true} [:maybe string?]]
   [:account-id [:ref "AccountId"]]
   [:party-id string?]
   [:name string?]
   [:currency [:ref "Currency"]]
   [:product-id string?]
   [:version-id string?]
   [:account-status [:enum :opening :opened :closing :closed]]
   [:payment-addresses {:optional true}
    [:maybe [:vector [:ref "PaymentAddress"]]]]
   [:created-at {:optional true} [:maybe string?]]
   [:updated-at {:optional true} [:maybe string?]]])

(def CreateAccountRequest
  [:map
   {:json-schema/example examples/CreateAccountRequest}
   [:party-id string?]
   [:name string?]
   [:currency [:ref "Currency"]]
   [:product-id string?]])

(def CreateAccountResponse [:ref "Account"])

(def AccountList
  [:map
   {:json-schema/example examples/AccountList}
   [:accounts
    [:vector [:ref "Account"]]]
   [:links {:optional true}
    [:map
     [:next {:optional true} string?]
     [:prev {:optional true} string?]]]])

(def CloseAccountResponse [:ref "Account"])

(def registry
  (components-registry [#'AccountId #'ScanAddress #'PaymentAddress #'Account
                        #'CreateAccountRequest #'CreateAccountResponse
                        #'AccountList #'CloseAccountResponse]))

