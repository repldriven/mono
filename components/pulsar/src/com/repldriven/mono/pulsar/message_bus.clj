(ns com.repldriven.mono.pulsar.message-bus
  (:require
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.pulsar.core :as pulsar]
    [clojure.core.async :as async])
  (:import
    (org.apache.pulsar.client.api Message)))

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

(defn- delivery
  [consumer {:keys [message data]}]
  (let [^Message msg message]
    {:data data
     :key (when (.hasKey msg) (.getKey msg))
     :ack (fn [] (pulsar/acknowledge consumer msg))
     ;; The broker redelivers, and once maxRedeliverCount is hit
     ;; dead-letters the poison message.
     :nack (fn [t]
             (log/error t "Consumer handler threw; negative-acknowledging")
             (pulsar/negative-acknowledge consumer msg))}))

(defrecord PulsarConsumer [consumer timeout stop-ch performers name]
  message-bus/Consumer
    (subscribe [this handler-fn] (.subscribe this handler-fn {}))
    (subscribe [_ handler-fn opts]
      (let [{:keys [c stop]} (pulsar/receive consumer timeout)
            source (async/chan 1 (map (partial delivery consumer)))]
        (reset! stop-ch stop)
        (async/pipe c source)
        (message-bus/perform
         source
         handler-fn
         {:performers performers :key-fn (:key-fn opts) :name name})
        {:stop stop}))
    (unsubscribe [_] (stop-loop stop-ch))
    ;; One broker consumer, so one subscription: stopping it by name and
    ;; stopping the consumer's only loop are the same act.
    (unsubscribe [_ _subscription] (stop-loop stop-ch)))
