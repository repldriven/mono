(ns com.repldriven.mono.kafka.message-bus
  (:require
    [com.repldriven.mono.kafka.core :as kafka]

    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]

    [clojure.core.async :as async]))

(defrecord KafkaProducer [producer]
  message-bus/Producer
    (send [_ message] (kafka/send producer message))
    ;; `:key` in opts becomes the record key, so Kafka's default
    ;; partitioner hashes every message for one entity to one partition —
    ;; which is where its ordering guarantee lives.
    (send [_ message opts] (kafka/send producer message opts)))

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
            (try (handler-fn data)
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
