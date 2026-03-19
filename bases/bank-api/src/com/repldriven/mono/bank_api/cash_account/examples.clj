(ns com.repldriven.mono.bank-api.cash-account.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def CashAccountNotFound
  {:value {:title "REJECTED"
           :type "cash-accounts/not-found"
           :status 404
           :detail "Cash account not found"}})

(def CashAccountAlreadyExists
  {:value {:title "REJECTED"
           :type "cash-accounts/exists"
           :status 422
           :detail "Customer already has an account of this kind"}})

(def ProductNotPublished
  {:value {:title "REJECTED"
           :type "cash-account/product-not-published"
           :status 422
           :detail "No published product version found"}})

(def InvalidCurrency
  {:value {:title "REJECTED"
           :type "cash-account/invalid-currency"
           :status 422
           :detail "Currency not allowed for this product"}})

(def registry
  (examples-registry [#'CashAccountNotFound #'CashAccountAlreadyExists
                      #'ProductNotPublished #'InvalidCurrency]))

(def CashAccount
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

(def CashAccountId (:account-id CashAccount))

(def CashAccountList {:cash-accounts [CashAccount]})

(def CreateCashAccountRequest
  (select-keys CashAccount [:party-id :name :currency :product-id]))

(def CreateCashAccountResponse CashAccount)

(def CloseCashAccountResponse CashAccount)
