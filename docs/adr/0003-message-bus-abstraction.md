# 3. Message-bus abstraction with pluggable backends

<!-- tessl-plugin: design -->

## Status

Accepted.

## Context

A system built from these bricks needs asynchronous messaging in three
places: commands from an API to the processors that handle them,
replies from those processors back to the originating handler, and
events between components.

Several broker technologies are credible candidates: Apache Pulsar,
Kafka, RabbitMQ, NATS, MQTT brokers, AWS SNS/SQS, Google Pub/Sub. Each
has its own trade-offs in throughput, ordering guarantees, retention,
replay, schema support, and operational complexity. Locking a codebase
into the API surface of any one of them would make future changes —
switching brokers, supporting more than one, running in-process for
tests — costly across every component that uses messaging.

We also want to be able to run tests without spinning up a broker
container every time. An in-process backend that exercises the same
producer / consumer code paths but over Clojure channels is useful for
tests, REPL work, and small-footprint deployments.

## Decision

We will keep the message bus behind an abstraction. The `message-bus`
brick exposes two small protocols — `Producer` and `Consumer` — and
the operations over them: `send`, `subscribe` and `unsubscribe`.

Two kinds of backend implement the protocols, and two rules bind them:

- **A broker backend for production.** Each broker is its own brick —
  `kafka`, `pulsar`, `mqtt` — and extends the protocols in its own
  `message-bus` namespace.
- **A Clojure-channels backend** (the `local` namespace inside
  `message-bus`), used in tests and small-footprint deployments.
- **Components consume the abstraction only.** Component code
  (`command`, `event`, and so on) consumes the abstraction only. No
  component requires a broker brick or `local` directly.
- **The system definition binds the backend.** The system definition
  decides which backend a given producer or consumer binds to at
  startup; a base bare-requires the backend bricks it may bind, so
  their component kinds register.

## Consequences

Easier:

- Backends are swappable. Moving a system between brokers is a new
  binding in its system config, not a sweep across every component
  that uses messaging.
- Tests that do not specifically exercise the broker can run on the
  channels backend, no Testcontainers required. Faster, less flaky,
  and they cover the same producer / consumer code paths as
  production.
- The choice of broker is reduced to a system-config concern.
  Re-evaluation costs are bounded.

Harder:

- The abstraction must not leak. Broker-specific concepts —
  subscription / consumer-group models, topic hierarchies,
  namespaces, message properties, delayed delivery — have to stay
  behind the backend boundary. This requires discipline when adding
  features.
- Two kinds of backend mean two test surfaces. Behaviour that holds on
  channels (ordered, in-process, no network) but fails on a broker
  backend (or vice versa) is a real risk. Production-shaped
  integration tests must run on the production backend.
- Some broker-specific features may not fit the abstraction cleanly.
  If we ever need, say, Pulsar's geo-replication or Kafka's exactly-
  once semantics, we either extend the interface (burdening every
  backend) or accept it as backend-only — neither is free.
