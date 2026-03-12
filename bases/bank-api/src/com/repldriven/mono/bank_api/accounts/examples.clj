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

(def registry (examples-registry [#'AccountNotFound #'AccountAlreadyExists]))

(def Account
  {:organization-id "org_01JMABC"
   :account-id "acc_01JMABC123DEF456"
   :party-id "pty_01JMABC"
   :name "Jane Doe"
   :currency "GBP"
   :account-status :opened})

(def AccountId (:account-id Account))

(def AccountList {:accounts [Account]})

(def CreateAccountRequest (select-keys Account [:party-id :name :currency]))

(def CreateAccountResponse Account)

(def CloseAccountResponse Account)


