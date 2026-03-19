(ns com.repldriven.mono.bank-queenswood.system
  (:require
    [com.repldriven.mono.bank-queenswood.core :as core]
    [com.repldriven.mono.system.interface :as system]))

(def ^:private bootstrap
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (core/bootstrap {:record-db (:record-db config)
                                        :record-store (:record-store config)}
                                       config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component}
   :system/instance-schema map?})

(system/defcomponents :queenswood {:bootstrap bootstrap})
