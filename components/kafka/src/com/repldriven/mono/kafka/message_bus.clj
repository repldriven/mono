(ns com.repldriven.mono.kafka.message-bus
  (:require
    [com.repldriven.mono.kafka.core :as kafka]

    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.utility.interface :as util]

    [clojure.core.async :as async])
  (:import
    (org.apache.kafka.clients.consumer ConsumerRecord)
    (org.apache.kafka.clients.producer RecordMetadata)))

(defn- messaging-attributes
  [{:keys [operation topic partition offset key group-id]}]
  ;; `messaging.operation`, `messaging.kafka.message.offset` and
  ;; `messaging.kafka.consumer.group` are the older conventions' names for
  ;; the operation, offset and group, kept beside the current ones because
  ;; some backends' queue views still read only those.
  (util/assoc-some {:messaging.system "kafka"
                    :messaging.operation.type operation
                    :messaging.operation
                    (if (= "send" operation) "publish" operation)
                    :messaging.destination.name topic
                    :messaging.destination.partition.id (str partition)
                    :messaging.kafka.offset offset
                    :messaging.kafka.message.offset offset}
                   :messaging.kafka.message.key
                   key
                   :messaging.consumer.group.name
                   group-id
                   :messaging.kafka.consumer.group
                   group-id))

(defn- trace-sent
  [result key]
  (when (instance? RecordMetadata result)
    (let [^RecordMetadata metadata result]
      (doseq [[k v] (messaging-attributes {:operation "send"
                                           :topic (.topic metadata)
                                           :partition (.partition metadata)
                                           :offset (.offset metadata)
                                           :key key})]
        (telemetry/set-attribute k v))))
  result)

(defn- delivered
  [data ^ConsumerRecord record group-id]
  (message-bus/with-delivery
   data
   (messaging-attributes {:operation "process"
                          :topic (.topic record)
                          :partition (.partition record)
                          :offset (.offset record)
                          :key (some-> ^bytes (.key record)
                                       (String. "UTF-8"))
                          :group-id group-id})))

(defrecord KafkaProducer [producer]
  message-bus/Producer
    (send [_ message] (trace-sent (kafka/send producer message) nil))
    ;; `:key` in opts becomes the record key, so Kafka's default
    ;; partitioner hashes every message for one entity to one partition —
    ;; which is where its ordering guarantee lives.
    (send [_ message opts]
      (trace-sent (kafka/send producer message opts) (:key opts))))

;; A subscription is stopped by telling the polling thread to stop and
;; forgetting its handles.
(defn- stop-loop
  [handles]
  (when-let [{:keys [stop]} @handles]
    (async/put! stop :stop)
    (reset! handles nil)))

;; `handles` holds the {:c :stop :ack} map from receive, because Kafka's
;; acknowledgements are queued to the polling thread rather than called on the
;; consumer directly — see kafka.kafka.consumer.
(defrecord KafkaConsumer [consumer timeout handles]
  message-bus/Consumer
    (subscribe [_ handler-fn]
      (let [{:keys [c] :as hs} (kafka/receive consumer timeout)]
        (reset! handles hs)
        (async/go-loop []
          (when-let [{:keys [message data]} (async/<! c)]
            ;; A throw from handler-fn must not kill the go-loop, which
            ;; would wedge the subscription silently. Commit on success; on
            ;; any throw ask for redelivery, which the consumer bounds so
            ;; a poison message cannot loop forever.
            (try (handler-fn (delivered data message (:group-id consumer)))
                 (kafka/acknowledge hs message)
                 (catch Throwable t
                   (log/error t "Consumer handler threw; asking for redelivery")
                   (kafka/negative-acknowledge hs message)))
            (recur)))
        {:stop (:stop hs)}))
    (unsubscribe [_] (stop-loop handles))
    ;; One consumer group member, so one subscription: stopping it by name
    ;; and stopping the consumer's only loop are the same act.
    (unsubscribe [_ _subscription] (stop-loop handles)))
