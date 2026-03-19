(ns com.repldriven.mono.bank-transaction.system
  (:require
    [com.repldriven.mono.bank-transaction.core :as core]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (core/->TransactionProcessor config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component}
   :system/instance-schema some?})

(system/defcomponents :transactions {:processor processor})
