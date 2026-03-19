(ns com.repldriven.mono.mqtt.system.core
  (:require
    [com.repldriven.mono.mqtt.system.components :as components]
    [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :mqtt
                      {:client components/client
                       :consumers components/consumers
                       :message-bus-consumers components/message-bus-consumers
                       :message-bus-producers components/message-bus-producers
                       :producers components/producers})
