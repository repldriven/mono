(ns com.repldriven.mono.message-bus.interface
  (:refer-clojure :exclude [send])
  (:require
    com.repldriven.mono.message-bus.system.core
    [com.repldriven.mono.message-bus.core :as core]
    [com.repldriven.mono.message-bus.performers :as performers]
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

(defn with-delivery
  "Attach `attributes`, the OpenTelemetry messaging attributes describing
  how `message` was delivered -- `messaging.system`, the destination,
  partition, offset and consumer group -- to `message` as metadata, for
  a backend to call before handing a message to a subscriber. A message
  that cannot carry metadata is returned unchanged."
  [message attributes]
  (core/with-delivery message attributes))

(defn delivery
  "The messaging attributes a backend attached to `message` with
  `with-delivery`, for the consumer's span, or nil where it attached
  none."
  [message]
  (core/delivery message))

(defn subscribe
  "Subscribe `handler-fn` to the named consumer, and return the
  subscription.

  Every subscriber on a consumer receives every message, as a broker
  gives each of its own subscriptions a copy. Pass the returned value to
  `unsubscribe` to stop this subscription alone.

  The consumer hands each message to one of its configured `performers`,
  chosen by the message's key: one performer handles its messages one
  at a time, in the order they arrived, so messages sharing a key keep
  their order and messages with different keys run concurrently. A
  message sent without a key goes to the next performer in turn. Where
  the consumer's configuration sets `max-in-flight`, no more performers
  than its limit run a handler at once, as `perform` describes.

  `opts`:
  - `:key-fn` — a narrower performer key, a function of the message.
    Use one only where every message sharing it was sent under one key,
    or it reorders what the topic keeps in order. A nil result falls
    back to the send key."
  ([bus consumer-name handler-fn]
   (core/subscribe bus consumer-name handler-fn))
  ([bus consumer-name handler-fn opts]
   (core/subscribe bus consumer-name handler-fn opts)))

(defn perform
  "Run `handler-fn` over the deliveries on `source`, for a backend's
  `subscribe` to call. Each delivery is a map of `:data`, what the
  handler is given; `:key`, the key it was sent under or nil; `:ack`, a
  fn of no arguments called when the handler returns; and `:nack`, a fn
  of the throwable called when it throws.

  Each delivery goes to one of `:performers` (1 when absent) by a hash
  of its performer key, `:key-fn` applied to its `:data` and the send
  key where that gives nil, or to the next in turn when it has no key.
  A performer runs on a thread of its own and handles its deliveries one
  at a time, in order. Each holds at most `:queue` deliveries (16 when
  absent), and taking from `source` waits while the chosen one is full.
  Each performer's thread is named `<:name>-performer-<i>` while it runs,
  so a span or a thread dump says which subscription it serves.

  `:max-in-flight` limits how many performers run a handler at once, as
  `subscription-limiter` builds it: a performer takes a permit before
  its handler and releases it after, holding its delivery while it
  waits, so its queue fills and taking from `source` waits. Which
  performer a delivery goes to does not change. The limiter's limit,
  active handlers and capacity are published as the
  `mono.message_bus.subscription.limit`, `.active` and `.capacity`
  gauges, with `:name` as `messaging.consumer.group.name`.

  Closing `source` stops it. Returns a channel that closes once every
  performer has finished, or the limiter's anomaly where
  `:max-in-flight` is invalid, before anything starts."
  [source handler-fn opts]
  (performers/perform source handler-fn opts))

(defn subscription-limiter
  "The concurrency limiter for a consumer of `performers` performers
  configured with `max-in-flight`: nil where it is nil, a fixed limit
  where it is an integer, and a dynamic one where it is a map, whose
  `:max` defaults to `performers`. A backend calls it at start to refuse
  options before a subscription is made.

  Returns the limiter, nil, or a rejection — of category
  `:message-bus/invalid-max-in-flight` where the map's `:max` exceeds
  `performers`, or the `concurrency-limit/limiter` one."
  [performers max-in-flight]
  (performers/subscription-limiter performers max-in-flight))

(defn unsubscribe
  "Stop `subscription`, or every subscription on the named consumer when
  none is given — which is what a component shutting down wants."
  ([bus consumer-name] (core/unsubscribe bus consumer-name))
  ([bus consumer-name subscription]
   (core/unsubscribe bus consumer-name subscription)))
