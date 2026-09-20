(ns com.repldriven.mono.message-bus.protocol (:refer-clojure :exclude [send]))

;; `opts` is per-send, backend-specific, and optional: a backend that has
;; nothing to do with a given key ignores it. `:key` is the one every
;; partitioned backend understands — records sharing a key share a
;; partition, which is the only ordering guarantee Kafka or Pulsar offer.
(defprotocol Producer
  (send [this message]
        [this message opts]))

;; `subscribe` returns a subscription, which `unsubscribe` takes to stop
;; that one alone. Without the argument it stops every subscription on the
;; consumer, which is what a component shutting down wants.
(defprotocol Consumer
  (subscribe [this handler-fn])
  (unsubscribe [this]
               [this subscription]))
