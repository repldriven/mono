(ns com.repldriven.mono.kafka.core
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.kafka.kafka.consumer :as consumer]
    [com.repldriven.mono.kafka.kafka.producer :as producer]
    [com.repldriven.mono.kafka.kafka.topics :as topics]))

;;;; producer
(defn send
  ([p data] (producer/send p data))
  ([p data opts] (producer/send p data opts)))

(defn send-async
  ([p data] (producer/send-async p data))
  ([p data opts] (producer/send-async p data opts)))

;;;; consumer
(defn receive [c timeout-ms] (consumer/receive c timeout-ms))

(defn acknowledge [handles message] (consumer/acknowledge handles message))

(defn negative-acknowledge
  [handles message]
  (consumer/negative-acknowledge handles message))

;;;; admin
(defn topics [admin ts] (topics/create admin ts))
