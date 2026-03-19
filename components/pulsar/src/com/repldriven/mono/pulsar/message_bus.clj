(ns com.repldriven.mono.pulsar.message-bus
  (:require
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
            (handler-fn data)
            (pulsar/acknowledge consumer message)
            (recur)))))
    (unsubscribe [_]
      (when-let [stop @stop-ch]
        (async/put! stop :stop)
        (reset! stop-ch nil))))
