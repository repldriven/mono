(ns com.repldriven.mono.message-bus.local
  (:require
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.protocol :as proto]
    [clojure.core.async :as async]))

(defrecord LocalProducer [ch]
  proto/Producer
    (send [_ message] (async/put! ch message)))

(defrecord LocalConsumer [ch stop-ch]
  proto/Consumer
    (subscribe [_ handler-fn]
      (let [stop (async/chan)]
        (reset! stop-ch stop)
        (async/go-loop []
          (let [[msg port] (async/alts! [ch stop])]
            (when (not= port stop)
              ;; A throw must not kill the in-memory loop (which would
              ;; wedge the channel). No ack/redelivery here — the local
              ;; bus is at-most-once — so log and move on.
              (try (handler-fn msg)
                   (catch Throwable t
                     (log/error t "Local bus handler threw; dropping message")))
              (recur))))))
    (unsubscribe [_]
      (when-let [stop @stop-ch]
        (async/close! stop)
        (reset! stop-ch nil))))
