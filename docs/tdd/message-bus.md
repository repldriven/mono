# Message bus

> **Status: implemented.** The `message-bus` brick, its `local` backend
> and the three broker backends all exist. This document records the
> design of subscription on the bus — one copy per subscriber, and a
> subscription a caller can stop on its own — and the reason the local
> backend is built around a `mult`.

## Objective

A component subscribing to a topic gets every message on it, whatever
else is subscribed and whichever backend is bound. Stopping one
subscription leaves the rest running.

In scope: the `Consumer` protocol's contract for `subscribe` and
`unsubscribe`, the `local` backend that meets it over core.async, and
the same arity on the `kafka`, `pulsar` and `mqtt` backends.

Out of scope: which backend a system binds, decided in
[ADR-0003](../adr/0003-message-bus-abstraction.md); the shape of the
messages, decided in
[ADR-0004](../adr/0004-avro-for-message-payloads.md); delivery
guarantees stronger than at-most-once on the local backend, which is
a Known Limitation rather than a design goal.

## Background

The `message-bus` brick keeps a broker behind two protocols, `Producer`
and `Consumer`, with `send`, `subscribe` and `unsubscribe` over them.
A `Bus` holds named producers and consumers; the system definition
binds each name to a backend at startup.

The `local` backend is the one tests and small-footprint deployments
run on. `message-bus/local-bus` takes a list of channel names and
builds, for each, one core.async channel feeding a `LocalProducer` and
a `LocalConsumer`:

```yaml
system:
  message-bus:
    bus: !system/component
      system/component-kind: message-bus/local-bus
      channels: [command, reply]
```

A broker gives every subscription its own copy of a topic: a Kafka
consumer group, a Pulsar subscription, an MQTT client subscription each
see every message. Two components on one topic — a processor and a
webhook runner, say — both act on each message.

The `command` brick's dispatcher and processor are the subscribers in
this workspace. Each subscribes once to its own channel, keeps the
subscription, and stops it when the component stops.

## Solution

### One copy per subscriber

A core.async channel is a queue: each take removes a message, so two
loops taking from one channel compete for it, and which loop wins any
message is a race. The local backend therefore never hands the channel
to a subscriber. `local-bus` wraps each channel in a `mult`, and every
`subscribe` taps it:

```clojure
(let [ch (async/chan 10)]
  {:ch ch :mult (async/mult ch)})
```

The `LocalProducer` keeps the channel and puts to it. The
`LocalConsumer` keeps the `mult` and an atom of its subscriptions. On
`subscribe` it creates a tap with a buffer of ten and a stop channel,
taps the `mult`, records the pair, and starts a `go-loop` that `alts!`
between the tap and the stop channel until the stop channel closes. A
message put to the channel reaches every tap, so every subscriber.

### A subscription a caller can stop

`subscribe` returns the subscription, and `unsubscribe` takes it:

```clojure
(defprotocol Consumer
  (subscribe [this handler-fn])
  (unsubscribe [this] [this subscription]))
```

With a subscription, the local backend untaps that tap, closes its
stop channel and removes it from the atom; the other subscriptions run
on. Without one, it stops every subscription on the consumer, which is
what a component shutting down wants. Before the second arity existed
`unsubscribe` could only mean "all", and a second `subscribe` on the
same consumer overwrote the first's stop channel, leaving a loop that
nothing could stop and that kept taking messages.

On a broker backend one consumer is one subscription — a Pulsar
consumer, a Kafka group member, an MQTT topic subscription — so the two
arities do the same thing there. Each backend implements the second by
delegating to the first, so a caller writes the same code against any
backend.

The `interface.clj` docstrings state the contract: every subscriber
receives every message, and the returned value stops that subscription
alone.

### The first slice

The whole design landed as one change: the protocol arity, the `mult`
in `local-bus`, the `LocalConsumer` rewrite, the second arity on
`kafka`, `pulsar` and `mqtt`, and the tests below.

### Tests

The local backend's interface test drives a system with two channels
and asserts, on one of them, that:

- two subscribers both receive a message sent once, with the same id;
- stopping one subscription by value delivers nothing further to it
  and everything to the other;
- stopping without a subscription stops both, so a message sent
  afterwards reaches neither;
- a handler that throws does not stop the loop, and the next message is
  delivered.

The existing single-subscriber, reply-channel and keyed-send tests hold
unchanged, since one subscriber on a `mult` behaves as one on a
channel.

## Alternatives Considered

- **A channel per subscriber, fed by a `pub`/`sub` on a topic key.**
  Rejected: `pub` routes by a function of the message, and every
  message on a channel goes to every subscriber of that channel, so the
  topic function would be a constant — a `mult` with an extra step.
- **Refusing a second `subscribe` on a consumer.** Rejected: it makes
  the local backend stricter than a broker, where a second subscription
  on a topic is ordinary, so a system that works in production would
  fail in its tests.
- **Leaving `unsubscribe` as "stop all" and documenting it.** Rejected:
  a component stopping would silence every other subscriber on its
  channel, the competing-consumers bug in another guise. Since the
  broker backends have one subscription per consumer, the arity costs
  them nothing.
- **A subscription object with its own `stop`.** Rejected: it would be
  a third protocol for every backend to implement, when a value the
  consumer already knows how to stop carries the same information.

## Known Limitations

- **A message with no subscriber is dropped.** A `mult` takes from its
  source whether or not anything taps it, so a message sent before the
  first `subscribe` is gone, where the bare channel buffered ten. This
  is a broker with no retention. Every caller in this workspace
  subscribes at component start.
- **At-most-once.** A handler that throws is logged and its message
  dropped; there is no acknowledgement or redelivery on the local
  backend. Where that matters, a test binds a broker backend.
- **A slow subscriber holds the rest.** A `mult` delivers to every tap
  before taking the next message, so a tap whose buffer of ten is full
  blocks delivery to the others until it drains.

## References

- [ADR-0003](../adr/0003-message-bus-abstraction.md) — Message-bus
  abstraction with pluggable backends.
- [ADR-0004](../adr/0004-avro-for-message-payloads.md) — Avro for
  message payloads.
- [ADR-0005](../adr/0005-error-handling-with-anomalies.md) — Error
  handling with anomalies at interface boundaries.
- [ADR-0015](../adr/0015-comments-and-docstrings.md) — Comments and
  docstrings.
- [system-components](../recipes/code/system-components.md) —
  `system/defcomponents` and the component map.
- [system-configurations](../recipes/code/system-configurations.md) —
  the YAML that names a bus and its channels.
- [test-system](../recipes/test/test-system.md) — `with-test-system`,
  which the tests drive the bus through.
- [core.async mult](https://clojure.github.io/core.async/#clojure.core.async/mult)
  — the primitive the local backend fans out with.
