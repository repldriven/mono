(ns com.repldriven.mono.bank-idv.system
  (:require
    [com.repldriven.mono.bank-idv.core :as core]
    [com.repldriven.mono.bank-idv.watcher :as watcher]
    [com.repldriven.mono.system.interface :as system]))

(def ^:private processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (core/->IdvProcessor config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component}
   :system/instance-schema some?})

(def ^:private watcher-handler
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (watcher/idv-changelog-handler (:record-store config))))
   :system/config {:record-store system/required-component}
   :system/instance-schema fn?})

(def ^:private party-watcher-handler
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance (watcher/party-changelog-handler (:record-store config))))
   :system/config {:record-store system/required-component}
   :system/instance-schema fn?})

(system/defcomponents :idv
                      {:processor processor
                       :watcher-handler watcher-handler
                       :party-watcher-handler party-watcher-handler})
