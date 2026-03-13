(ns com.repldriven.mono.bank-api.accounts.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def AccountNotFound
  {:value {:title "REJECTED"
           :type "accounts/not-found"
           :status 404
           :detail "Account not found"}})

(def AccountAlreadyExists
  {:value {:title "REJECTED"
           :type "accounts/exists"
           :status 422
           :detail "Customer already has an account of this kind"}})

(def ProductNotPublished
  {:value {:title "REJECTED"
           :type "account/product-not-published"
           :status 422
           :detail "No published product version found"}})

(def InvalidCurrency
  {:value {:title "REJECTED"
           :type "account/invalid-currency"
           :status 422
           :detail "Currency not allowed for this product"}})

(def registry
  (examples-registry [#'AccountNotFound #'AccountAlreadyExists
                      #'ProductNotPublished #'InvalidCurrency]))

(def Account
  {:organization-id "org_01JMABC"
   :account-id "acc_01JMABC123DEF456"
   :party-id "pty_01JMABC"
   :name "Jane Doe"
   :currency "GBP"
   :product-id "prd_01JMABC"
   :version-id "prv_01JMABC"
   :account-status :opened
   :payment-addresses [{:scheme "uk.scan"
                        :identifier {:scan {:sort-code "040004"
                                            :account-number "12345678"}}}]})

(def AccountId (:account-id Account))

(def AccountList {:accounts [Account]})

(def CreateAccountRequest
  (select-keys Account [:party-id :name :currency :product-id]))

(def CreateAccountResponse Account)

(def CloseAccountResponse Account)


