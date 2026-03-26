(ns com.repldriven.mono.bank-interest.system
  (:require
    [com.repldriven.mono.bank-interest.core :as core]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (core/->InterestProcessor config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component}
   :system/instance-schema some?})

(system/defcomponents :interest {:processor processor})
