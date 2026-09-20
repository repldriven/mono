(ns com.repldriven.mono.pulsar.message-bus
  (:require
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.pulsar.core :as pulsar]
    [clojure.core.async :as async]))

(defrecord PulsarProducer [producer]
  message-bus/Producer
    (send [_ message] (pulsar/send producer message))
    ;; Translated rather than passed through: Pulsar's message builder
    ;; takes a string-keyed conf and throws on any key it does not know.
    (send [_ message opts]
      (let [{:keys [key]} opts]
        (if key
          (pulsar/send producer message {"key" key})
          (pulsar/send producer message)))))

(defn- stop-loop
  [stop-ch]
  (when-let [stop @stop-ch]
    (async/put! stop :stop)
    (reset! stop-ch nil)))

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
            (recur)))
        {:stop stop}))
    (unsubscribe [_] (stop-loop stop-ch))
    ;; One broker consumer, so one subscription: stopping it by name and
    ;; stopping the consumer's only loop are the same act.
    (unsubscribe [_ _subscription] (stop-loop stop-ch)))
