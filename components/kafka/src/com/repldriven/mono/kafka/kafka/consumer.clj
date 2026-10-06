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
    [com.repldriven.mono.kafka.kafka.offsets :as offsets]
    [com.repldriven.mono.kafka.kafka.producer :as producer]
    [com.repldriven.mono.kafka.kafka.serde :as serde]

    [com.repldriven.mono.error.interface :as error :refer [try-nom]]
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
;; maxRedeliverCount and a dead-letter topic; this does the same, with
;; `dead-letter-producer` playing the part of Pulsar's deadLetterTopic. Without
;; one, giving up means committing past the message — it is logged, but gone.
(def default-max-redeliveries 3)

(defn create
  [{:keys [conf topics schemas schema dead-letter-producer] :as opts}]
  (log/info "Creating Kafka consumer:" (:name opts))
  (try-nom
   :kafka/consumer-create
   "Failed to create Kafka consumer"
   (let [props (config/->properties defaults conf)
         instance (KafkaConsumer. props)]
     (.subscribe instance ^java.util.Collection (vec topics))
     {:instance instance
      :topics (vec topics)
      :group-id (.getProperty props "group.id")
      ;; Resolved here, not at receive time, so a misnamed schema fails
      ;; when the system starts rather than on the first message.
      :schema (when schema (get schemas (name schema)))
      :dead-letter-producer dead-letter-producer
      :max-redeliveries
      (get opts :max-redeliveries default-max-redeliveries)})))

(defn- ->partition
  ^TopicPartition [[topic partition]]
  (TopicPartition. topic partition))

(defn- record-partition
  [^ConsumerRecord record]
  [(.topic record) (.partition record)])

(defn- assigned
  [^KafkaConsumer instance]
  (into #{}
        (map (fn [^TopicPartition tp] [(.topic tp) (.partition tp)]))
        (.assignment instance)))

(defn- dead-letter!
  "Forward a message that has exhausted its redeliveries. The value is sent
  as it arrived — raw bytes, unparsed — because whatever is wrong with it is
  exactly what someone will want to look at."
  [producer ^ConsumerRecord record]
  (let [result (producer/send producer (.value record))]
    (when (error/anomaly? result)
      (log/error "Failed to dead-letter message; committing past it anyway"
                 {:topic (.topic record)
                  :partition (.partition record)
                  :offset (.offset record)
                  :anomaly result}))
    result))

(defn- apply-ack
  "Applies one queued acknowledgement to the polling thread's state. A
  commit or a message given up on finishes its offset; a redelivery marks
  it waiting and its partition for a seek."
  [{:keys [max-redeliveries dead-letter-producer]} state
   {:keys [op
           ^ConsumerRecord
           record]}]
  (let [tp (record-partition record)
        offset (.offset record)
        k (conj tp offset)
        finish (fn [state]
                 (-> state
                     (update :attempts dissoc k)
                     (update :offsets offsets/finished tp offset)))]
    (case op
      :commit (finish state)
      :redeliver
      (let [n (inc (get-in state [:attempts k] 0))]
        (if (>= n max-redeliveries)
          (do (log/error "Giving up on message after"
                         n
                         (if dead-letter-producer
                           "attempts; dead-lettering it"
                           "attempts; committing past it")
                         {:topic (.topic record)
                          :partition (.partition record)
                          :offset offset})
              (when dead-letter-producer
                (dead-letter! dead-letter-producer record))
              ;; Finished either way: a message that cannot be
              ;; dead-lettered is still one this consumer must stop
              ;; replaying, and the log above is the record of it.
              (finish state))
          (-> state
              (assoc-in [:attempts k] n)
              (update :offsets offsets/waiting tp offset)
              (update :seek (fnil conj #{}) tp)))))))

(defn- settle!
  "Seeks each partition a redelivery named back to its earliest waiting
  offset, then commits every partition whose position has moved. Runs on
  the polling thread."
  [^KafkaConsumer instance state]
  (let [state (update state :offsets offsets/retain (assigned instance))]
    (doseq [tp (:seek state)]
      (when-let [offset (offsets/earliest-waiting (:offsets state) tp)]
        (.seek instance (->partition tp) (long offset))))
    (let [positions (offsets/to-commit (:offsets state))
          committed? (or (empty? positions)
                         (try (.commitSync instance
                                           ^java.util.Map
                                           (into {}
                                                 (map (fn [[tp p]]
                                                        [(->partition tp)
                                                         (OffsetAndMetadata.
                                                          (long p))]))
                                                 positions))
                              true
                              ;; A rebalance can refuse a commit; the
                              ;; positions are tried again next pass.
                              (catch Throwable t
                                (log/warn t "Kafka commit failed; retrying")
                                false)))]
      (cond-> (dissoc state :seek)
              committed?
              (update :offsets offsets/committed positions)))))

(defn- record->message
  [schema ^ConsumerRecord record]
  {:message record
   :data (let [v (.value record)]
           (if schema (serde/deserialize schema v) v))})

(defn receive
  "Continuously poll a Kafka consumer and put messages on a channel.
  Returns `{:c chan :stop chan :ack chan}`. Send anything to `:stop` to stop
  receiving; send acknowledgements to `:ack` via `acknowledge` /
  `negative-acknowledge`."
  [{:keys [^KafkaConsumer instance schema] :as consumer} timeout-ms]
  (let [c (async/chan)
        stop (async/chan 1)
        ack (async/chan 1024)
        duration (Duration/ofMillis timeout-ms)]
    (async/thread
     (try
       (loop [state {:attempts {} :offsets {}}]
         ;; Acks first: applying them before the next poll means a
         ;; redelivery seek takes effect immediately rather than a batch
         ;; later.
         (let [state (settle! instance
                              (loop [state state]
                                (if-let [a (async/poll! ack)]
                                  (recur (apply-ack consumer state a))
                                  state)))]
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
                   ;; wedge the stop signal with it. Each record is counted
                   ;; out before it goes, so its ack finds it.
                   [stopped? state]
                   (reduce (fn [[_ state] ^ConsumerRecord record]
                             (let [state (update state
                                                 :offsets
                                                 offsets/handed-out
                                                 (record-partition record)
                                                 (.offset record))
                                   [_ port] (async/alts!!
                                             [[c
                                               (record->message schema record)]
                                              stop])]
                               (if (= port stop)
                                 (reduced [true state])
                                 [false state])))
                           [false state]
                           (seq records))]
               (when-not stopped? (recur state))))))
       (finally
        (try (.close instance)
             (catch Throwable t (log/warn t "Failed to close Kafka consumer")))
        (async/close! c)
        (async/close! stop)
        (async/close! ack))))
    {:c c :stop stop :ack ack}))

(defn acknowledge
  "Mark `message` done. Queued for the polling thread, which owns the consumer
  and commits past it once its partition has nothing earlier outstanding.
  Returns nil."
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
