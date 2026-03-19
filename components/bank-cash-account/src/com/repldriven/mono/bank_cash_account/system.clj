(ns com.repldriven.mono.bank-cash-account.system
  (:require
    [com.repldriven.mono.bank-cash-account.core :as core]
    [com.repldriven.mono.bank-cash-account.watcher :as watcher]
    [com.repldriven.mono.system.interface :as system]))

(def ^:private processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (core/->CashAccountProcessor config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component}
   :system/instance-schema some?})

(def ^:private watcher-handler
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (watcher/cash-account-changelog-handler (:record-store
                                                                config))))
   :system/config {:record-store system/required-component}
   :system/instance-schema fn?})

(system/defcomponents :cash-accounts
                      {:processor processor :watcher-handler watcher-handler})
