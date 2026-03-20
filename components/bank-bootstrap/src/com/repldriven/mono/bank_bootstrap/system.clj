(ns com.repldriven.mono.bank-bootstrap.system
  (:require
    [com.repldriven.mono.bank-bootstrap.core :as core]
    [com.repldriven.mono.system.interface :as system]))

(def ^:private internal
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (core/bootstrap {:record-db (:record-db config)
                                        :record-store (:record-store config)}
                                       config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component}
   :system/instance-schema map?})

(system/defcomponents :bootstrap {:internal internal})
