(ns com.repldriven.mono.message-bus.local
  (:require
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.protocol :as proto]
    [clojure.core.async :as async]))

(def ^:private tap-buffer 10)

(defrecord LocalProducer [ch]
  proto/Producer
    (send [_ message] (async/put! ch message))
    ;; No partitions to key: a core.async channel is already ordered.
    (send [_ message _opts] (async/put! ch message)))

(defn- handler-loop
  [tap stop handler-fn]
  (async/go-loop []
    (let [[msg port] (async/alts! [tap stop])]
      (when (not= port stop)
        ;; A throw must not kill the in-memory loop (which would
        ;; wedge the channel). No ack/redelivery here — the local
        ;; bus is at-most-once — so log and move on.
        (try (handler-fn msg)
             (catch Throwable t
               (log/error t "Local bus handler threw; dropping message")))
        (recur)))))

(defn- stop-subscription
  [mult {:keys [tap stop]}]
  (async/untap mult tap)
  (async/close! stop))

(defrecord LocalConsumer [mult subscriptions]
  proto/Consumer
    (subscribe [_ handler-fn]
      (let [subscription {:tap (async/chan tap-buffer) :stop (async/chan)}]
        (async/tap mult (:tap subscription))
        (swap! subscriptions conj subscription)
        (handler-loop (:tap subscription) (:stop subscription) handler-fn)
        subscription))
    (unsubscribe [_]
      (doseq [subscription (first (reset-vals! subscriptions []))]
        (stop-subscription mult subscription)))
    (unsubscribe [_ subscription]
      (swap! subscriptions (fn [subs] (vec (remove #(= % subscription) subs))))
      (stop-subscription mult subscription)))
