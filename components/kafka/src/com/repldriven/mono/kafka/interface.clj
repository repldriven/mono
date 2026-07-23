(ns com.repldriven.mono.kafka.interface
  "Apache Kafka producers and consumers, wired as system components.

  The same bargain the pulsar brick makes: the official Java client wrapped
  directly, configuration as data, and failures returned as anomalies rather
  than thrown. Values are serialised with the `avro` brick, so schemas live in
  `command-schema` / `event-schema` resources rather than in a registry.

  Delivery is at-least-once and duplicates are expected: redelivery works by
  seeking a partition back, so replaying one message replays those after it.

  Kafka's client is not thread-safe, so a consumer is owned by the one thread
  that polls it. `receive` therefore returns `{:c chan :stop chan :ack chan}` —
  the pulsar shape plus an `:ack` channel, through which acknowledgements are
  queued for that thread. `acknowledge` and `negative-acknowledge` take those
  handles rather than the consumer.

  Component-kinds are registered under `:kafka` via this brick's `system`
  namespace; `message-bus-producers` and `message-bus-consumers` make it usable
  from `command`, `command-processor` and `event-processor` unchanged."
  (:refer-clojure :exclude [send])
  (:require
    com.repldriven.mono.kafka.system.core

    [com.repldriven.mono.kafka.core :as core]))

;;;; producer

(defn send
  "Send `data` to the producer's topic, returning the RecordMetadata or an
  anomaly. Waits for the broker's acknowledgement.

  Args:
  - producer: a started `kafka/producer` component.
  - data: the value, serialised with the producer's schema when it has one.
  - opts: `:key` for the partition key, `:timeout-ms` for the ack wait."
  ([producer data] (core/send producer data))
  ([producer data opts] (core/send producer data opts)))

(defn send-async
  "Like `send`, but returns the java Future without waiting.

  Args:
  - producer: a started `kafka/producer` component.
  - data: the value to send.
  - opts: `:key` for the partition key."
  ([producer data] (core/send-async producer data))
  ([producer data opts] (core/send-async producer data opts)))

;;;; consumer

(defn receive
  "Poll `consumer` continuously, returning `{:c chan :stop chan :ack chan}`.
  Take messages `{:message record :data value}` from `:c`; send anything to
  `:stop` to finish, which also closes the consumer.

  Args:
  - consumer: a started `kafka/consumer` component.
  - timeout-ms: poll timeout, which also bounds how quickly `:stop` is seen."
  [consumer timeout-ms]
  (core/receive consumer timeout-ms))

(defn acknowledge
  "Commit a message's offset, queued to the polling thread.

  Args:
  - handles: the map returned by `receive`.
  - message: the `ConsumerRecord` from `:message`."
  [handles message]
  (core/acknowledge handles message))

(defn negative-acknowledge
  "Ask for a message to be redelivered, by seeking its partition back. Bounded
  by the consumer's `max-redeliveries`, after which it is logged and skipped.

  Note this is not Pulsar's per-message nack: a seek rewinds the partition, so
  every message after the failed one is replayed too. Handlers have to tolerate
  duplicates.

  Once `max-redeliveries` is reached the message goes to the consumer's
  `dead-letter-producer`, as raw bytes. A consumer without one drops it
  instead, with a log line as the only record.

  Args:
  - handles: the map returned by `receive`.
  - message: the `ConsumerRecord` from `:message`."
  [handles message]
  (core/negative-acknowledge handles message))

;;;; admin

(defn topics
  "Create topics that do not already exist. Returns their names, or an
  anomaly.

  Args:
  - admin: a started `kafka/admin` component.
  - topics: maps of `:topic`, `:partitions`, `:replication-factor`."
  [admin topics]
  (core/topics admin topics))
