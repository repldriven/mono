# 41. A dynamic concurrency limit keeps work in flight at measured capacity

<!-- tessl-plugin: design -->

## Status

**Accepted**

## Context

Two numbers bound how much work a process takes on at once. The server's
`max-in-flight` turns away a request beyond it, and a consumer's
`performers` fixes how many messages it handles at once. Both are chosen
at deployment, from a guess at what the store or service behind the
handlers can take, and the right number differs with every platform a
workspace runs on and moves as that store's load changes.

Throughput rises with the work in flight until the store is saturated,
and from there only latency rises. The point where it stops rising, the
knee, is the most throughput the store gives with the least waiting:
by Little's law it is the store's capacity times its unloaded latency.
A number below the knee turns away work the store had room for; a
number above it admits work that only waits, and times out after being
done when the store slows. The property wanted is a limit that finds the
knee by measuring, without a number configured for each platform,
follows it as the store changes, and admits a ramp in load as fast as
the store can take it, without touching what keeps messages in order.

The shortlist:

- **A fixed number, tuned per platform.** Rejected as the only means:
  it is right on the platform and at the load it was tuned for. It
  stays, as a limit that never moves.
- **Scaling processes on CPU or lag.** Rejected as the mechanism: an
  autoscaler adds processes over minutes, and a store saturated by them
  needs less work in flight, not more callers. It composes with this
  decision.
- **Resizing a consumer's performers.** Rejected: a message's performer
  is a hash of its key modulo their number, so changing the number moves
  a key to another performer while its earlier messages wait in the old
  one's queue, breaking the order
  [ADR-0040](0040-a-consumer-hands-each-message-to-a-performer-chosen-by-its-key.md)
  keeps.
- **A limit moved by latency against a tolerance**, the gradient
  algorithm of Netflix's
  [concurrency-limits](https://github.com/Netflix/concurrency-limits).
  Rejected: it targets a ratio of latency rather than throughput, so it
  settles past the knee with work waiting, and it grows by a fraction of
  √limit a window, so a ramp outruns it and work the store had room for
  is turned away. The library is rejected as a dependency too: the
  limiter and its estimator are smaller than the brick that would wrap
  it under [ADR-0011](0011-one-component-per-third-party-library.md).
- **A limit that backs off on a latency budget**, additive-increase,
  multiplicative-decrease against a configured timeout. Rejected: a
  limit removes only the latency queueing adds, so where a store's
  unloaded latency exceeds the budget the limit falls to its minimum and
  turns away work it cannot make faster, and the budget is one more
  number for each platform.
- **A limit held at the measured knee.** As TCP's
  [BBR](https://queue.acm.org/detail.cfm?id=3022184) sizes what it has
  in flight: measure the most throughput delivered while the limit was
  what bound, and the lowest latency, and hold the limit at their
  product with headroom; double it while it binds and throughput keeps
  rising; and on a cycle probe above the knee and drain below it. On a
  consumer, a performer takes a permit before running its handler, so
  the number of performers, and the performer each key goes to, never
  change.

## Decision

Bound the work a process takes on with a limit held at the knee of its
throughput — the most throughput measured while the limit bound, times
the lowest latency measured, with headroom — found by measuring rather
than configured, in a `concurrency-limit` brick that the server and
`message-bus` share. On a consumer the limit caps how many performers
run a handler at once and never changes how many performers there are,
so the order ADR-0040 keeps is untouched.

The decision has these parts:

- Configure a limit as `max-in-flight`, one key with one shape on the
  server and on every consumer: an integer is a fixed limit, a map of
  `initial`, `min`, `max`, `headroom` and `windows`, each with a
  default, is a dynamic one, and an absent key sets no limit.
- Never configure a throughput or a latency: estimate capacity as the
  highest throughput of the last `windows` windows in which the limit
  bound, and the floor as the lowest latency of those windows.
- Measure each admitted unit's latency from taking its permit to
  releasing it, and each window's throughput, mean latency and whether
  the limit turned work away or held it waiting; step the limit once
  per window by a pure function of the window and the estimator's
  state.
- Without a capacity estimate, double the limit after each window in
  which it bound, until doubling raises throughput by less than a
  quarter, so a ramp is admitted as fast as the store can take it.
- With an estimate, hold the limit at `headroom` times capacity times
  floor, raising it by a quarter for one window in eight to find
  capacity that has grown, and setting it to three-quarters of the knee
  for another to drain what waits and re-measure the floor.
- Leave the estimate as it is after a window in which the limit did not
  bind, so quiet traffic never lowers it, and search again once no
  window in the last `windows` bound.
- Keep the limit between `min` and `max`; on a consumer `max` defaults
  to, and never exceeds, `performers`.
- Count a handler that throws in neither throughput nor latency, so a
  quick failure moves no estimate.
- Turn away a request beyond the server's limit, as `max-in-flight` does
  now, rather than queue it; have a performer wait for a permit, which
  holds its queue and so the backend's delivery.
- Keep `performers` fixed from configuration, as ADR-0040 decides.
- Publish each limiter's limit, in-flight count and capacity estimate
  as gauges, beside the server's count of requests turned away.
- Test the estimator as a pure function against a simulated workload,
  never by timing a running server.

### Worked example

A server that turns work away beyond a fixed 200 today, made dynamic
from the same starting point, and a Kafka consumer whose sixteen
performers run as many handlers at once as its store can serve:

```yaml
jetty-adapter: !system/component
  system/component-kind: server/jetty-adapter
  handler: !system/local-ref handler
  max-in-flight: {initial: 200}

message-bus-consumers: !system/component
  system/component-kind: kafka/message-bus-consumers
  command:
    consumer: !system/local-ref consumers.command
    performers: 16
    max-in-flight: {}
```

On a platform whose store saturates past 200, the limit doubles while
throughput rises and holds above 200; on one that saturates below it,
the drain windows find the floor and the limit settles below 200. The
configuration is the same on both.

## Consequences

Easier:

- A process runs at the most throughput the store gives, found without
  a number configured for each platform, and follows it as the store
  changes.
- A ramp in load is admitted as fast as the limit doubles, so work the
  store has room for is not turned away.
- A store whose every request slows loses capacity as its floor rises,
  so their product, the knee, and the limit with it stay where the
  store needs them.
- One key and one shape bound the server and every consumer, and an
  integer keeps meaning what `max-in-flight` means now.
- A consumer's concurrency changes without a key moving, so ADR-0040's
  order tests hold with a limit on.
- The estimator is a pure function of a window and its state, so how it
  behaves under a ramp, a saturated store or a slowdown is a test, not
  an experiment in production.

Harder:

- A limit that moves is harder to read than a number: the limit and
  capacity gauges are read beside `mono.server.request.rejected` and
  request latency.
- One window in eight drains below the knee and gives up a quarter of
  that window's throughput, about three percent overall.
- Capacity and floor age over the same windows but not from the same
  window, so for up to a cycle after the store changes one estimate is
  stale and the limit is too low or too high.
- The search overshoots by up to one doubling before it finds
  throughput flat, so for a window the store has twice the knee in
  flight.
- Latency and throughput mix the work behind one limiter: a route or
  message type slow by nature moves the estimate for everything sharing
  it. Such work wants a limiter of its own, a judgement for each system.
- Contention that shows as a fast failure, a conflict that throws and is
  redelivered, moves no estimate. ADR-0040's conflict rate is still read
  by a person.
- Each process estimates on its own. Processes share no limit, and agree
  only through what the store gives each of them.
- On Jetty's platform threads a request waits in Jetty's queue before
  the limiter sees it; on virtual threads Jetty bounds nothing, and
  `max` bounds the search, so it is set from what is behind the
  handlers.
- The estimator's constants and defaults are chosen against the
  simulation, not production. Drift between them and what a platform
  needs shows only in the gauges, which are the audit.
