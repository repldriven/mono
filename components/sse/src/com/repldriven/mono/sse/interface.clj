(ns com.repldriven.mono.sse.interface
  "Server-sent events: a registry of open streams by topic, the loop that
  holds one open, and the Ring response that writes it.

  A topic is whatever a stream is opened for — a customer, an account, a
  job — and any value may name one. What is published to a topic reaches
  every stream open on it at that moment and nothing else: the registry
  keeps no history, so a caller that must not lose an event keeps it
  and hands it back through `serve`'s `:pending`.

  Events are maps. `:id`, where an event carries one, identifies it: an
  event already sent on a stream is not sent on it again, and it becomes
  the frame's `id:`, which a reconnecting client sends back as
  `Last-Event-ID`."
  (:require
    [com.repldriven.mono.sse.registry :as registry]
    [com.repldriven.mono.sse.response :as response]
    [com.repldriven.mono.sse.stream :as stream]))

(def default-keep-alive-ms
  "How long a stream waits with nothing to send before writing a
  keep-alive, when `serve` is given none: fifteen seconds, inside the
  idle timeout of the proxies a stream usually passes through."
  stream/default-keep-alive-ms)

(defn registry
  "A new registry, open, holding no streams."
  []
  (registry/registry))

(defn publish!
  "Offer `event` to every stream open on `topic`. Nothing is kept where
  none is open.

  Args:
  - registry: as `registry` answers.
  - topic: the topic the event is for.
  - event: a map, identified by its `:id` where it carries one."
  [registry topic event]
  (registry/publish! registry topic event))

(defn open-count
  "How many streams are open on `topic`. A client going away reaches the
  registry only when a write to its stream fails, a keep-alive at most
  later, so this answers what the registry still holds rather than what
  the client has let go.

  Args:
  - registry: as `registry` answers.
  - topic: the topic."
  [registry topic]
  (registry/open-count registry topic))

(defn close!
  "Close the registry: every open stream ends, and any opened afterwards
  ends at once.

  Args:
  - registry: as `registry` answers."
  [registry]
  (registry/close! registry))

(defn serve
  "Hold a stream open on `topic`, calling `emit` with nil once it is
  subscribed; then with each event `:pending` answers; then with each
  event published to the topic, skipping any already sent; and with nil
  whenever the keep-alive passes with nothing to send. `:on-sent` is
  told each event `emit` took.

  Returns nil when the registry closes, the anomaly when `emit` or
  `:pending` returns one, and propagates what `emit` throws. The stream
  is unsubscribed however it ends.

  Args:
  - registry: as `registry` answers.
  - topic: the topic to subscribe to.
  - emit: a function of one argument, an event or nil. An anomaly
    returned ends the stream, and the event is not told to `:on-sent`.
  - opts: a map of
    - `:keep-alive-ms` — the wait before a keep-alive, defaulting to
      `default-keep-alive-ms`.
    - `:pending` — a function of `:last-event-id`, answering the events
      to send before any published, oldest first, or an anomaly.
    - `:on-sent` — a function of an event, called once `emit` took it.
      What it returns is not read.
    - `:last-event-id` — the `Last-Event-ID` the client sent, passed to
      `:pending`."
  [registry topic emit opts]
  (stream/serve registry topic emit opts))

(defn frame
  "One event in the wire format, or a keep-alive comment for nil. A
  `:data` holding line breaks is written as one `data:` line each.

  Args:
  - message: `{:event :id :data :retry}`, `:data` a string and the rest
    optional; or nil."
  [message]
  (response/frame message))

(defn response
  "A Ring response holding an event stream open for as long as `run`
  runs. `run` is handed `emit`, which writes an event as a frame whose
  `data:` is the event as JSON, or a keep-alive for nil, and answers an
  `:sse/closed` anomaly once the client has gone — which is what makes
  it the `emit` `serve` takes.

  Args:
  - run: a function of `emit`. An anomaly it returns is logged, at debug
    for `:sse/closed` and as an error otherwise.
  - opts: a map of
    - `:event` — the `event:` name every frame carries, or none."
  [run opts]
  (response/response run opts))
