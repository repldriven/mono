(ns com.repldriven.mono.pulsar.system.core
  (:require
    com.repldriven.mono.pulsar.system.reader.edn
    com.repldriven.mono.pulsar.system.reader.yml
    [com.repldriven.mono.pulsar.system.components :as components]
    [com.repldriven.mono.system.interface :as system]))

;; system components
(system/defcomponents
 :pulsar
 {:broker-url components/broker-url
  :http-service-url components/http-service-url
  :admin components/admin
  :client components/client
  :consumer components/consumer
  :consumers components/consumers
  :crypto-key-pair-file-reader components/crypto-key-pair-file-reader
  :crypto-key-pair-file-readers components/crypto-key-pair-file-readers
  :crypto-key-pair-generator components/crypto-key-pair-generator
  :crypto-key-reader components/crypto-key-reader
  :crypto-key-readers components/crypto-key-readers
  :message-bus-consumers components/message-bus-consumers
  :message-bus-producers components/message-bus-producers
  :namespaces components/namespaces
  :producers components/producers
  :producer components/producer
  :readers components/readers
  :reader components/reader
  :schemas components/schemas
  :tenants components/tenants
  :topics components/topics})

