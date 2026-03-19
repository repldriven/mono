(ns com.repldriven.mono.bank-cash-account.interface
  (:require
    com.repldriven.mono.bank-cash-account.system

    [com.repldriven.mono.bank-cash-account.commands :as commands]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.bank-schema.interface :as schema]))

(defn open
  "Opens a cash account with balances. Returns account map or
  anomaly."
  [config data]
  (error/let-nom> [pb (commands/open-account config data)]
    (schema/pb->CashAccount pb)))
