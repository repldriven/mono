(ns com.repldriven.mono.kafka.kafka.consumer
  "Consuming from Kafka onto a channel.

  `receive` returns `{:c chan :stop chan :ack chan}`, the same shape the pulsar
  brick returns (plus `:ack`), because `message-bus` and `command` are written
  against it.

  Two things about Kafka shape this namespace, and neither applies to Pulsar:

  - `KafkaConsumer` is **not thread-safe**. One thread owns it for its whole
    life — polling, committing, seeking and closing all happen there. That is
    why acknowledgement is a channel rather than a function call: an ack raised
    on a handler's thread is queued and applied by the owning thread.
  - `.poll` returns a *batch*, and advances the consumer's position as it does.
    Not committing therefore does not redeliver anything to a running consumer;
    only seeking back does. And a seek moves the whole partition, so asking for
    one message again also replays everything after it — Kafka has no
    per-message negative acknowledgement. Handlers must tolerate duplicates."
  (:require
    [com.repldriven.mono.kafka.kafka.config :as config]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :refer [try-nom]]
    [com.repldriven.mono.log.interface :as log]

    [clojure.core.async :as async])
  (:import
    (java.time Duration)
    (org.apache.kafka.clients.consumer ConsumerRecord
                                       KafkaConsumer
                                       OffsetAndMetadata)
    (org.apache.kafka.common TopicPartition)))

(def ^:private byte-array-deserializer
  "org.apache.kafka.common.serialization.ByteArrayDeserializer")

(def ^:private defaults
  {"key.deserializer" byte-array-deserializer
   "value.deserializer" byte-array-deserializer
   ;; Offsets are committed by this namespace, after a handler has actually
   ;; run. Auto-commit would acknowledge on poll, losing any message whose
   ;; handler had not finished when the process died.
   "enable.auto.commit" "false"
   "auto.offset.reset" "earliest"})

;; A handler that keeps failing would otherwise be redelivered forever, since
;; seeking back is the only way to redeliver. Pulsar bounds this with
;; maxRedeliverCount and a dead-letter topic; without a DLQ, this bounds it by
;; giving up: log loudly and commit past the message. Configurable per
;; consumer.
(def default-max-redeliveries 3)

(defn create
  [{:keys [conf topics schemas schema] :as opts}]
  (log/info "Creating Kafka consumer:" (:name opts))
  (try-nom
   :kafka/consumer-create
   "Failed to create Kafka consumer"
   (let [instance (KafkaConsumer. (config/->properties (merge defaults conf)))]
     (.subscribe instance (vec topics))
     {:instance instance
      :topics (vec topics)
      ;; Resolved here, not at receive time, so a misnamed schema fails
      ;; when the system starts rather than on the first message.
      :schema (when schema (get schemas (name schema)))
      :max-redeliveries
      (get opts :max-redeliveries default-max-redeliveries)})))

(defn- ->partition
  ^TopicPartition [^ConsumerRecord record]
  (TopicPartition. (.topic record) (.partition record)))

(defn- commit!
  [^KafkaConsumer instance ^ConsumerRecord record]
  (.commitSync instance
               {(->partition record) (OffsetAndMetadata. (inc (.offset
                                                               record)))}))

(defn- redeliver!
  [^KafkaConsumer instance ^ConsumerRecord record]
  (.seek instance (->partition record) (.offset record)))

(defn- apply-ack!
  "Applies one queued acknowledgement. Runs on the polling thread, which is
  the only thread allowed to touch the consumer."
  [{:keys [instance max-redeliveries]} attempts
   {:keys [op
           ^ConsumerRecord
           record]}]
  (let [k [(.topic record) (.partition record) (.offset record)]]
    (case op
      :commit (do (commit! instance record) (dissoc attempts k))
      :redeliver
      (let [n (inc (get attempts k 0))]
        (if (>= n max-redeliveries)
          (do (log/error "Giving up on message after"
                         n
                         "attempts; committing past it"
                         {:topic (.topic record)
                          :partition (.partition record)
                          :offset (.offset record)})
              (commit! instance record)
              (dissoc attempts k))
          (do (redeliver! instance record) (assoc attempts k n)))))))

(defn- record->message
  [schema ^ConsumerRecord record]
  {:message record
   :data (let [v (.value record)]
           (if schema (avro/deserialize-same schema v) v))})

(defn receive
  "Continuously poll a Kafka consumer and put messages on a channel.
  Returns `{:c chan :stop chan :ack chan}`. Send anything to `:stop` to stop
  receiving; send acknowledgements to `:ack` via `acknowledge` /
  `negative-acknowledge`."
  [{:keys [^KafkaConsumer instance schema] :as consumer} timeout-ms]
  (let [c (async/chan)
        stop (async/chan 1)
        ack (async/chan 100)
        duration (Duration/ofMillis timeout-ms)]
    (async/thread
     (try
       (loop [attempts {}]
         ;; Acks first: applying them before the next poll means a
         ;; redelivery seek takes effect immediately rather than a batch
         ;; later.
         (let [attempts (loop [attempts attempts]
                          (if-let [a (async/poll! ack)]
                            (recur (apply-ack! consumer attempts a))
                            attempts))]
           (if (async/poll! stop)
             nil
             (let [records
                   (try (.poll instance duration)
                        ;; Anything thrown here would otherwise kill this
                        ;; thread silently, leaving a consumer that appears
                        ;; connected but never delivers again. Log with the
                        ;; class and message (a repeat throw gets its stack
                        ;; folded by the JIT), back off to rate-limit the
                        ;; spam, and carry on.
                        (catch Throwable t
                          (log/error t
                                     "Kafka poll threw; recurring"
                                     {:exception-class (.getName (class t))
                                      :message (.getMessage t)})
                          (Thread/sleep 500)
                          nil))
                   ;; Race each put against stop: if the caller stopped
                   ;; reading, a plain >!! would block here forever and
                   ;; wedge the stop signal with it.
                   stopped? (reduce (fn [_ record]
                                      (let [[_ port]
                                            (async/alts!!
                                             [[c
                                               (record->message schema
                                                                record)]
                                              stop])]
                                        (if (= port stop)
                                          (reduced true)
                                          false)))
                                    false
                                    (seq records))]
               (when-not stopped? (recur attempts))))))
       (finally
        (try (.close instance)
             (catch Throwable t (log/warn t "Failed to close Kafka consumer")))
        (async/close! c)
        (async/close! stop)
        (async/close! ack))))
    {:c c :stop stop :ack ack}))

(defn acknowledge
  "Commit `message`'s offset. Queued for the polling thread, which owns the
  consumer. Returns nil."
  [{:keys [ack]} message]
  (async/put! ack {:op :commit :record message})
  nil)

(defn negative-acknowledge
  "Ask for `message` to be redelivered, by seeking the partition back to it.
  After `max-redeliveries` attempts the message is logged and committed past
  instead, so a poison message cannot loop forever. Returns nil."
  [{:keys [ack]} message]
  (async/put! ack {:op :redeliver :record message})
  nil)

(defn close
  "Stops the receive loop, which closes the consumer on its own thread."
  [{:keys [stop]}]
  (when stop (async/put! stop :stop))
  nil)
