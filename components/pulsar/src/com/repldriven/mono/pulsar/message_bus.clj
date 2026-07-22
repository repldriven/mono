(ns com.repldriven.mono.pulsar.message-bus
  (:require
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.pulsar.core :as pulsar]
    [clojure.core.async :as async]))

(defrecord PulsarProducer [producer]
  message-bus/Producer
    (send [_ message] (pulsar/send producer message)))

(defrecord PulsarConsumer [consumer timeout stop-ch]
  message-bus/Consumer
    (subscribe [_ handler-fn]
      (let [{:keys [c stop]} (pulsar/receive consumer timeout)]
        (reset! stop-ch stop)
        (async/go-loop []
          (when-let [{:keys [message data]} (async/<! c)]
            ;; A throw from handler-fn must not kill the go-loop (which
            ;; would silently wedge the whole channel). Ack on success;
            ;; on any throw, nack so the broker redelivers and — once
            ;; maxRedeliverCount is hit — dead-letters the poison message.
            (try (handler-fn data)
                 (pulsar/acknowledge consumer message)
                 (catch Throwable t
                   (log/error t
                              "Consumer handler threw; negative-acknowledging")
                   (pulsar/negative-acknowledge consumer message)))
            (recur)))))
    (unsubscribe [_]
      (when-let [stop @stop-ch]
        (async/put! stop :stop)
        (reset! stop-ch nil))))
