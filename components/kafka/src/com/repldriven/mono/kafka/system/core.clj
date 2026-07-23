(ns com.repldriven.mono.kafka.system.core
  (:require
    [com.repldriven.mono.kafka.system.components :as components]

    [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :kafka
                      {:bootstrap-servers components/bootstrap-servers
                       :admin components/admin
                       :topics components/topics-component
                       :producer components/producer-component
                       :producers components/producers
                       :consumer components/consumer-component
                       :consumers components/consumers
                       :message-bus-producers components/message-bus-producers
                       :message-bus-consumers components/message-bus-consumers})
