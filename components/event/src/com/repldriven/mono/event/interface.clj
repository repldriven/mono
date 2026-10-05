(ns com.repldriven.mono.event.interface
  "Event envelope construction, publishing, and processing on top of
  the message-bus. Envelopes carry trace context so consumers resume
  a parent span; `process` wraps the handler in a child span and
  turns a failure — thrown or returned as an anomaly — into a
  redelivery request to the bus."
  (:require
    [com.repldriven.mono.event.publisher :as publisher]
    [com.repldriven.mono.event.processor :as processor]))

(defn envelope
  "Build a skeleton event envelope.

  Args:
  - event-name: event name string
  - causation-id: ID of the causing command or event
  - correlation-id: correlation ID for tracing

  Returns an event envelope map. Assoc :payload before
  publishing."
  [event-name causation-id correlation-id]
  (publisher/envelope event-name causation-id correlation-id))

(defn publish
  "Publish an event envelope to the event channel.

  Args:
  - bus: message-bus instance
  - envelope: event envelope map
  - opts: optional map with keys:
    - :event-channel - keyword for the event channel
      (default :event)
    - :key - partition key; events sharing one keep their
      order. Passed through to the bus, never derived from
      the envelope
  Any other key is passed to the bus untouched.

  Returns the result of message-bus/send, which may be
  an anomaly."
  ([bus envelope] (publisher/publish bus envelope))
  ([bus envelope opts] (publisher/publish bus envelope opts)))

(defn process
  "Process event envelopes via message-bus.

  Args:
  - bus: message-bus instance
  - handler-fn: function that takes an event envelope
    and processes it
  - opts: optional map with keys:
    - :event-channel - keyword for receiving events
      (default :event)
    - :key-fn - a narrower performer key than the send key, a fn
      of the envelope; see `message-bus/subscribe`

  A handler-fn that throws, or returns an anomaly, leaves the
  event unacknowledged: the bus redelivers it, and dead-letters
  it once the backend's redelivery limit is reached. Handlers
  must therefore tolerate duplicates.

  Returns: {:stop (fn [])} — call stop to unsubscribe"
  ([bus handler-fn] (processor/process bus handler-fn))
  ([bus handler-fn opts] (processor/process bus handler-fn opts)))
