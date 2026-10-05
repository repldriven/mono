(ns com.repldriven.mono.message-bus.local
  (:require
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.performers :as performers]
    [com.repldriven.mono.message-bus.protocol :as proto]
    [clojure.core.async :as async]))

(def ^:private tap-buffer 10)

;; The key travels with the message, so a subscriber's performers order by
;; it as a broker's would.
(defrecord LocalProducer [ch]
  proto/Producer
    (send [_ message] (async/put! ch {:message message}))
    (send [_ message opts] (async/put! ch {:message message :key (:key opts)})))

(defn- delivery
  [{:keys [message key]}]
  {:data message
   :key key
   :ack (fn [])
   ;; No redelivery here — the local bus is at-most-once — so log and move
   ;; on.
   :nack (fn [t] (log/error t "Local bus handler threw; dropping message"))})

(defn- deliveries
  [tap stop]
  (let [source (async/chan)]
    (async/go-loop []
      (let [[sent port] (async/alts! [tap stop])]
        (if (or (= port stop) (nil? sent))
          (async/close! source)
          (do (async/>! source (delivery sent)) (recur)))))
    source))

(defn- stop-subscription
  [mult {:keys [tap stop]}]
  (async/untap mult tap)
  (async/close! stop))

(defrecord LocalConsumer [mult subscriptions performers]
  proto/Consumer
    (subscribe [this handler-fn] (proto/subscribe this handler-fn {}))
    (subscribe [_ handler-fn opts]
      (let [{:keys [tap stop] :as subscription} {:tap (async/chan tap-buffer)
                                                 :stop (async/chan)}]
        (async/tap mult tap)
        (swap! subscriptions conj subscription)
        (performers/perform (deliveries tap stop)
                            handler-fn
                            {:performers performers :key-fn (:key-fn opts)})
        subscription))
    (unsubscribe [_]
      (doseq [subscription (first (reset-vals! subscriptions []))]
        (stop-subscription mult subscription)))
    (unsubscribe [_ subscription]
      (swap! subscriptions (fn [subs] (vec (remove #(= % subscription) subs))))
      (stop-subscription mult subscription)))
