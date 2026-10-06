# 41. Concurrency adapts to latency through a limit, not by resizing performers

<!-- tessl-plugin: design -->

## Status

**Proposed**

## Context

Two numbers bound how much work a process takes on at once. The server's
`max-in-flight` turns away a request beyond it, and a consumer's
`performers` fixes how many messages it handles at once. Both are chosen
at deployment, from a guess at what the store or service behind the
handlers can take.

What that store or service can take moves: another workload's load, a
cold cache, a failover each change how many requests a process can
usefully have outstanding. A number chosen for the good case is too high
when the store slows: requests wait behind slow ones, latency climbs,
and work that was admitted times out after being done. A number chosen
for the bad case turns work away the rest of the time. The property
wanted is a bound that follows what is downstream, falling as latency
rises and rising as it falls, without touching what keeps messages in
order.

The shortlist:

- **A fixed number, tuned per deployment.** Rejected as the only means:
  it is right at the load and latency it was tuned at, and wrong in the
  direction that matters when the store slows. It stays, as a limit that
  never moves.
- **Scaling processes on CPU or lag.** Rejected as the mechanism: an
  autoscaler adds processes over minutes, and a store slowing under load
  needs fewer requests outstanding, not more callers. It composes with
  this decision.
- **Resizing a consumer's performers.** Rejected: a message's performer
  is a hash of its key modulo their number, so changing the number moves
  a key to another performer while its earlier messages wait in the old
  one's queue, breaking the order
  [ADR-0040](0040-a-consumer-hands-each-message-to-a-performer-chosen-by-its-key.md)
  keeps. A safe resize drains every queue first, every time.
- **A concurrency-limit library**, such as Netflix's
  [concurrency-limits](https://github.com/Netflix/concurrency-limits).
  Rejected as a dependency: what is needed is a limiter and an update
  function, smaller than the brick that would wrap the library under
  [ADR-0011](0011-one-component-per-third-party-library.md). It is the
  reference for the gradient algorithm.
- **A limit that backs off on a latency budget**, additive-increase,
  multiplicative-decrease against a configured timeout. Rejected: a
  limit removes only the latency queueing adds, so where a store's
  unloaded latency exceeds the budget the limit falls to its minimum
  and turns away load it cannot make faster.
- **A limit that adapts to latency, apart from what keeps order.** Bound
  the handlers running at once by a limit that each one's latency moves,
  as TCP sizes its congestion window from round-trip times. On a
  consumer, a performer takes a permit before running its handler, so
  the number of performers, and the performer each key goes to, never
  change.

## Decision

Bound the work a process takes on with a concurrency limit that adapts
to the latency of what it admits, in a `concurrency-limit` brick that the
server and `message-bus` share. On a consumer the limit caps how many
performers run a handler at once and never changes how many performers
there are, so the order ADR-0040 keeps is untouched.

The decision has these parts:

- Configure a limit as `max-in-flight`, one key with one shape on the
  server and on every consumer: an integer is a fixed limit, a map of
  `initial`, `min`, `max` and `tolerance` is an adaptive one, and an
  absent key sets no limit.
- Measure each admitted unit's latency from taking its permit to
  releasing it, and step the limit once per window of samples by a pure
  function of the window and the algorithm's state.
- Move the limit by the gradient: shrink it as a window's latency rises
  past `tolerance` times the floor, the lowest window latency of the
  last minute or so, and grow it while latency stays within that.
- Never configure an acceptable latency as a number: a limit removes
  only the latency queueing adds, so `tolerance` says how much of it to
  accept, and a latency budget belongs to the client's timeout.
- Raise the limit only while what is in flight reaches half of it, so a
  quiet process never learns a ceiling it has not tried.
- Keep the limit between `min` and `max`; on a consumer `max` defaults
  to, and never exceeds, `performers`.
- Count a handler that throws as neither fast nor slow, so a quick
  failure never lowers the latency the limit compares against.
- Turn away a request beyond the server's limit, as `max-in-flight` does
  now, rather than queue it; have a performer wait for a permit, which
  holds its queue and so the backend's delivery.
- Keep `performers` fixed from configuration, as ADR-0040 decides.
- Publish each limiter's limit and in-flight count as gauges, beside the
  server's count of requests turned away.
- Test an algorithm as a pure function against a simulated workload,
  never by timing a running server.

### Worked example

A server that adapts between 4 and 200 requests at once, and a Kafka
consumer with sixteen performers of which at most the adaptive limit run
a handler at once:

```yaml
jetty-adapter: !system/component
  system/component-kind: server/jetty-adapter
  handler: !system/local-ref handler
  max-in-flight: {initial: 20, min: 4, max: 200}

message-bus-consumers: !system/component
  system/component-kind: kafka/message-bus-consumers
  command:
    consumer: !system/local-ref consumers.command
    performers: 16
    max-in-flight: {initial: 4}
```

When the store behind both slows, both limits fall: the server turns
away more requests with a 503, and the consumer runs fewer handlers,
leaving its other performers' queues to fill and the poll loop to wait.
Each key still goes to the performer it went to before.

## Consequences

Easier:

- A store that slows lowers how much each process sends it, and the
  limit comes back as latency does, with no redeploy and no number to
  re-tune.
- One key and one shape bound the server and every consumer, and an
  integer keeps meaning what `max-in-flight` means now.
- A consumer's concurrency changes without a key moving, so ADR-0040's
  order tests hold with a limit on.
- An algorithm is a pure function of a window and its state, so how it
  behaves under a load step or a slowdown is a test, not an experiment
  in production.

Harder:

- A limit that moves is harder to read than a number: whether a request
  was turned away depends on the latency before it, so the limit gauge
  is read beside `mono.server.request.rejected` and request latency.
- Latency mixes the work behind one limiter: a route or message type
  slow by nature lowers the limit for everything sharing it. Such work
  wants a limiter of its own, a judgement for each system.
- Contention that shows as a fast failure, a conflict that throws and is
  redelivered, is invisible to a latency signal, which counts the throw
  as neither. ADR-0040's conflict rate is still read by a person.
- Each process adapts on its own. Processes share no limit, and agree
  only through the latency they all see.
- A store that becomes slower for good reads as queueing until the
  floor follows it, and the limit sits low meanwhile.
- On Jetty's platform threads a request waits in Jetty's queue before
  the limiter sees it; on virtual threads Jetty bounds nothing, and
  `max-in-flight` is the only bound, so `max` is set from what is
  behind the handlers.
- The algorithm's tunables — window, tolerance, floor, smoothing — are
  defaults chosen against the simulation, not production. Drift between
  them and what a deployment needs shows only in the gauges, which are
  the audit.
