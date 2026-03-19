(ns com.repldriven.mono.test-schema.system
  (:require
    [com.repldriven.mono.test-schema.processor :as processor]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private pet-processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (processor/->PetProcessor config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component}
   :system/instance-schema some?})

(system/defcomponents :pets {:processor pet-processor})
