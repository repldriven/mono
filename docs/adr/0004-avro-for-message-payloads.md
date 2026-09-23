# 4. Avro for command and event payloads

<!-- tessl-plugin: design -->

## Status

Accepted.

## Context

Asynchronous messages on the message bus (ADR-0003) carry two kinds of
payload: commands from the HTTP API to processors, and events between
components. The wire format for those payloads is a real choice — it
affects schema evolution, producer/consumer contract clarity, ecosystem
fit, and message size.

This ADR is about *messaging* payloads only. A store's record format is
the store's decision — a workspace whose store mandates protobuf keeps
protobuf there — and isn't re-litigated here.

The shortlist for messaging:

- **Avro**, via [Lancaster](https://github.com/deercreeklabs/lancaster)
  on the Clojure side. Schema-first; schemas describe the
  producer/consumer contract; forward and backward compatibility rules
  are built in; binary encoding; idiomatic Clojure data shapes with
  keyword keys.
- **Protobuf**. Mature, fast, well-tooled. But proto's ecosystem is
  heavily tied to gRPC. Without gRPC you lose the service framework,
  the streaming semantics, and the bulk of proto's reason-to-use.
  [Protojure](https://github.com/protojure/lib) works for
  serialization-only, but that's the proto cost without the gRPC
  benefit.
- **JSON**. Schemaless. No producer-side enforcement; drift between
  producer and consumer goes silent until a runtime break. Larger
  encoding.
- **MessagePack / BSON**. Binary, but still schemaless. Same problem
  as JSON minus the human-readability.

## Decision

We will use Avro for all command and event payloads on the message
bus, via Lancaster on the Clojure side.

Three parts:

- **Where schemas live.** Schemas live in schema bricks of their own —
  `command-schema` and `event-schema` here — as resources, never
  beside the producer or consumer that binds to them.
- **When they bind.** Producers and consumers bind to a schema at
  registration; mismatch is caught at startup, not in production.
- **Why not protobuf.** If the bricks were built around gRPC we would
  likely have chosen protobuf and taken the gRPC service framework
  along with it. They aren't, so Avro is the better fit.

## Consequences

Easier:

- Schema evolution is built in. Adding optional fields, renaming with
  aliases, changing defaults — all explicit and tooling-checked.
  Reader/writer schema reconciliation lives in the library, not in
  our code.
- Producer/consumer contract is centralised in the schema bricks.
  Drift surfaces at registration, not when a downstream consumer
  chokes on an unexpected payload at 3am.
- Lancaster speaks Clojure data with keyword keys natively. This
  lines up with the kebab-case-keywords convention (ADR-0006) and
  removes a translation step at every wire boundary.
- Compact binary encoding compared to JSON; modest but real over time
  and on tight queues.
- Format-independent of transport. The message-bus abstraction
  (ADR-0003) doesn't know or care that the bytes are Avro. Swapping
  brokers takes the format with us unchanged.

Harder:

- A workspace whose store mandates its own record format carries two
  wire formats: that one for records, Avro for messaging. They serve
  different masters, but it's still cognitive load. Schemas for the
  same conceptual entity may exist in both forms.
- Avro's schema evolution rules are subtle. The
  forward/backward/full compatibility matrix needs to be understood
  by anyone changing a schema. Mistakes here are silent data
  corruption, the worst kind of bug.
- No browser-native Avro support. A browser client speaks JSON to the
  HTTP API; Avro stops at the message bus. This is by design — Avro
  was never meant for that — but it's a boundary to remember.
- Schemas in the schema bricks must be kept in step with the Clojure
  data structures producers and consumers actually emit and accept.
  Lancaster makes this less painful than raw Avro Java tooling, but
  it's still surface area to maintain.
