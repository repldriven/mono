(ns com.repldriven.mono.message-bus.local
  (:require
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
            (when (not= port stop) (handler-fn msg) (recur))))))
    (unsubscribe [_]
      (when-let [stop @stop-ch]
        (async/close! stop)
        (reset! stop-ch nil))))
