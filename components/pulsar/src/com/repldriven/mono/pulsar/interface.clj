(ns com.repldriven.mono.pulsar.interface
  (:refer-clojure :exclude [read send])
  (:require
    com.repldriven.mono.pulsar.system.core
    [com.repldriven.mono.pulsar.core :as core]))

;;;; producer
(defn send
  ([producer data] (core/send producer data))
  ([producer data opts] (core/send producer data opts)))

(defn send-async
  ([producer data] (core/send-async producer data))
  ([producer data opts] (core/send-async producer data opts)))

;;;; consumer
(defn receive [consumer timeout-ms] (core/receive consumer timeout-ms))

;;;; reader
(defn read [reader timeout-ms] (core/read reader timeout-ms))

;;;; admin
(defn admin-namespace-url
  [admin tenant namespace]
  (core/admin-namespace-url admin tenant namespace))
