# 40. A consumer hands each message to a performer chosen by its key

<!-- tessl-plugin: design -->

## Status

**Accepted**

## Context

A subscription handles one message at a time. Each backend's
`subscribe` runs a loop that takes a message, calls the handler, then
acknowledges it or asks for it again, so a consumer's rate is one over
its handler's time. Most handlers spend that time waiting on a store or
another service rather than computing, so the process's CPU sits idle
while the next message waits.

The property wanted is that one process handles messages about
different entities at the same time, while messages about one entity
keep the order they were sent in, on every backend `message-bus`
offers: the brokers, and the channels backend that tests and small
deployments run on. Without it, the only way to add concurrency is to
add partitions and a process to consume each one. That means a process
per unit of concurrency, a count fixed when the topic is created, and
nothing finer than the key a topic is partitioned by: a topic keyed by
tenant runs one tenant's work one message at a time, whatever the
tenant's entities.

The shortlist:

- **More partitions, and a process for each.** Rejected as the only
  means: it spends a process on each unit of concurrency and cannot
  work concurrently inside one key. It stays the way to spread work
  across processes.
- **`pmap`, or a pool sized by the processor count.** Rejected: in a
  container the JVM counts the processors its CPU limit allows, so a
  process limited to one CPU gets three threads from `pmap`, while a
  handler waiting on I/O wants far more in flight than it has CPUs.
  `pmap` also works a batch at a time and keeps no order by key.
- **A broker's own parallel consumer**, such as Confluent's
  [Parallel Consumer](https://github.com/confluentinc/parallel-consumer)
  in its key-ordering mode. Rejected: it is Kafka's alone, it replaces
  the consumer the abstraction is built on, and it is no longer
  maintained. It is the reference for how a positional commit tracks
  messages finished out of order.
- **Routing keys between processes at the broker**, as Pulsar's
  [Key_Shared](https://pulsar.apache.org/docs/next/concepts-messaging/#key_shared)
  subscription does. Rejected as the mechanism: one broker has it, and
  it spreads keys across processes rather than within one. It composes
  with this decision where a backend offers it.
- **A message dispatcher with key affinity, in `message-bus`.**
  Hohpe and Woolf's
  [Message Dispatcher](https://www.enterpriseintegrationpatterns.com/patterns/messaging/MessageDispatcher.html):
  one consumer on a channel hands each message to one of several
  performers. Choosing the performer by the message's key, as an
  ordered (or striped) executor does, makes it keep order per key.
  Written once in the layer every backend shares, it works the same on
  each of them.

## Decision

Every subscription hands each message to one of a configured number of
performers, chosen by the message's key, in `message-bus`, where every
backend shares it. A performer handles its messages one at a time in
the order they arrived, so messages sharing a key keep their order and
messages with different keys are handled concurrently. One performer is
a subscription as it was before.

The decision has these parts:

- Take a consumer's number of performers from its system configuration,
  as `performers`, defaulting to 1; never derive it from the processor
  count.
- Choose a message's performer by a hash of its performer key, which
  is the key it was sent under unless the subscriber names a narrower
  one; hand a message sent without a key to the next performer in turn.
- Let a subscriber narrow the performer key, as a function of the
  message, only where every message sharing the narrower key was sent
  under one key, so the narrowing never splits what the topic keeps in
  order.
- Run each performer on a thread of its own, virtual where the runtime
  offers them, never in a `go` block, whose pool is fixed and is not
  for work that blocks.
- Give each performer a bounded queue, and stop taking from the
  backend while any queue is full, so a slow key holds back delivery
  rather than filling memory.
- Acknowledge a message when its handler returns, and ask for it again
  when the handler throws, as a subscription does now.
- Reduce each backend's `subscribe` to delivering messages, each with
  its key and its acknowledgement; keep the performers, the choice of
  performer and the queues in `message-bus`, once.
- Have each backend apply acknowledgements that arrive out of order in
  its own way: Pulsar and the channels backend per message, and Kafka
  by committing, per partition, only the highest offset below which
  every message has been acknowledged.
- Carry the key a message was sent with through the channels backend
  to its subscribers, so a test runs the same concurrency and order a
  broker would.

### Worked example

A system's topics, the key each is sent under, and the performer key
its consumer would use:

| Topic | Sent under | Performer key |
|---|---|---|
| Commands to an account | the account | the account |
| A tenant's activity log | the tenant | the entry's subject, an account or an order, where an entry names one |
| Events about an order | the order | the order |
| Commands that create an entity | no key | the next performer in turn |

An activity log keyed by tenant runs on one partition per tenant
whatever its partition count, so a single busy tenant is handled one
entry at a time. Narrowed to the entry's subject, its performers handle
different accounts and orders at once, each in order. An entry naming
several subjects cannot be ordered against all of them by one key, so
such a log keeps the tenant as its key.

## Consequences

Easier:

- A consumer's concurrency is a number in its configuration, changed
  by a restart, with no topic recreated and no message moved.
  Partitions and processes stay the way to spread work across
  processes, chosen once for the most processes a consumer will need.
- One process works concurrently inside a key a topic is partitioned
  by, where adding partitions cannot.
- The channels backend runs the same concurrency as a broker, so a
  test that runs with several performers exercises what production
  runs.

Harder:

- Handlers run concurrently in one process. A handler that keeps
  state between messages needs that state to be safe across threads.
- A narrower performer key is a judgement for each subscriber: one that
  does not imply the send key reorders what the topic keeps in order,
  and nothing checks it. The subscriber's tests against each backend,
  with more than one performer, are where it shows.
- Kafka's committed offset waits for the slowest message in its
  partition, so a restart redelivers everything after it that had
  finished, which handlers already have to tolerate.
- More messages in flight means more contention in whatever the
  handlers write. Raising `performers` is measured against the store's
  conflict rate, not just the consumer's lag.
- A handler that throws is redelivered as each backend redelivers
  today, and a later message with its key may run first. The order kept
  is order on success, as it is with one performer.
