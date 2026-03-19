(ns com.repldriven.mono.pulsar.core
  (:refer-clojure :exclude [read send])
  (:require
    [com.repldriven.mono.pulsar.pulsar.admin :as admin]
    [com.repldriven.mono.pulsar.pulsar.consumer :as consumer]
    [com.repldriven.mono.pulsar.pulsar.producer :as producer]
    [com.repldriven.mono.pulsar.pulsar.reader :as reader]))

;;;; producer
(defn send
  ([producer data] (producer/send producer data))
  ([producer data opts] (producer/send producer data opts)))

(defn send-async
  ([producer data] (producer/send-async producer data))
  ([producer data opts] (producer/send-async producer data opts)))

;;;; consumer
(defn receive [consumer timeout-ms] (consumer/receive consumer timeout-ms))

(defn acknowledge [consumer message] (consumer/acknowledge consumer message))

;;;; reader
(defn read [reader timeout-ms] (reader/read reader timeout-ms))

;;;; admin
(defn admin-namespace-url
  [admin tenant namespace]
  (admin/namespace-url admin tenant namespace))
