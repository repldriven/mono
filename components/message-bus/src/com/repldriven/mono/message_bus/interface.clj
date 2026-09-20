(ns com.repldriven.mono.message-bus.interface
  (:refer-clojure :exclude [send])
  (:require
    com.repldriven.mono.message-bus.system.core
    [com.repldriven.mono.message-bus.core :as core]
    [com.repldriven.mono.message-bus.protocol :as protocol]))

;; Protocols are part of the public interface. Components that implement
;; message-bus producers or consumers require this namespace and extend
;; Producer/Consumer.
(def Producer protocol/Producer)
(def Consumer protocol/Consumer)

(defn send
  "Send `message` to the named producer.

  `opts` is per-send and backend-specific; a backend ignores what it
  has no use for. `:key` is understood by every partitioned backend:
  messages sharing a key are delivered in order, because they share a
  partition. Without one, records round-robin and per-entity order
  holds only while a topic has a single partition."
  ([bus producer-name message] (core/send bus producer-name message))
  ([bus producer-name message opts]
   (core/send bus producer-name message opts)))

(defn subscribe
  "Subscribe `handler-fn` to the named consumer, and return the
  subscription.

  Every subscriber on a consumer receives every message, as a broker
  gives each of its own subscriptions a copy. Pass the returned value to
  `unsubscribe` to stop this subscription alone."
  [bus consumer-name handler-fn]
  (core/subscribe bus consumer-name handler-fn))

(defn unsubscribe
  "Stop `subscription`, or every subscription on the named consumer when
  none is given — which is what a component shutting down wants."
  ([bus consumer-name] (core/unsubscribe bus consumer-name))
  ([bus consumer-name subscription]
   (core/unsubscribe bus consumer-name subscription)))
