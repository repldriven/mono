(ns com.repldriven.mono.event.interface
  "Event envelope construction, publishing, and processing on top of
  the message-bus. Envelopes carry trace context so consumers resume
  a parent span; `process` wraps the handler in a child span and
  funnels exceptions into `:event/process` anomalies."
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

  Returns: {:stop (fn [])} — call stop to unsubscribe"
  ([bus handler-fn] (processor/process bus handler-fn))
  ([bus handler-fn opts] (processor/process bus handler-fn opts)))
