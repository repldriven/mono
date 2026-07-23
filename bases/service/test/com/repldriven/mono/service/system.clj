(ns com.repldriven.mono.service.system
  (:require
    [com.repldriven.mono.service.pet-processor :as pet-processor]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private pet-processor-component
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (pet-processor/->PetProcessor config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component}
   :system/instance-schema some?})

(system/defcomponents :pets {:processor pet-processor-component})
