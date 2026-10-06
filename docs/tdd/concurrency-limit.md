# Concurrency limit

> **Status: proposal.** Nothing of the `concurrency-limit` brick exists.
> What the design changes — `server`'s `wrap-max-in-flight` and its
> `max-in-flight` key, `message-bus`'s performers and each consumer's
> `performers` key, `telemetry`'s counter — exists and is named as such
> in Background. Everything under Proposed Solution is the build list,
> and "The first slice" says what comes first.

## Objective

A process built on these bricks bounds the work it takes on at once by a
limit that follows the latency of what it admits: the server turns away
a request beyond the limit, and a consumer runs no more handlers at once
than it allows. This TDD decides the brick that holds the limit, the
algorithm that moves it, how the server and the performers take and
release permits, the configuration both read, the gauges that publish
it, and how an algorithm is tested.

In scope: the `concurrency-limit` brick — its `interface.clj`, the
limiter and its permits, the window, the gradient algorithm and the
options schema; the `max-in-flight` key on
`server/jetty-adapter` and on every consumer that has `performers`;
`telemetry/gauge`; registration in `mono-lib`, `mono-test-lib`, the
root `deps.edn` and the readme; and the tests.

Out of scope: adapting rather than fixing the limit, and leaving the
number of performers alone, decided in
[ADR-0041](../adr/0041-concurrency-adapts-to-latency-through-a-limit-not-by-resizing-performers.md);
which performer a message goes to, decided in
[ADR-0040](../adr/0040-a-consumer-hands-each-message-to-a-performer-chosen-by-its-key.md);
a limit shared between processes, separate limiters per route, and
scaling processes, which are Known Limitations rather than design goals.

## Background

- **The server's limit.** `server/wrap-max-in-flight`, in `shed.clj`,
  holds a `java.util.concurrent.Semaphore` of `n` permits. A request
  that gets one runs the handler and releases it in a `finally`; one
  that does not is answered at once with a 503, `Retry-After: 1` and a
  `server/overloaded` problem body, and adds one to the
  `mono.server.request.rejected` counter from `shed/rejected-counter`. A
  request under `/actuator/` is never turned away.
  `server/jetty-adapter`, in `system.clj`, wraps the whole handler when
  its config sets `max-in-flight`, a `pos-int?` in its config schema.
  The permit is released when the handler returns, so a body written
  after it, such as an SSE stream, holds none.
- **Jetty's threads.** `server/jetty-adapter` starts Jetty through
  `ring-jetty9-adapter`'s `run-jetty`, which builds a `QueuedThreadPool`
  of `max-threads` platform threads, 50 unless the adapter's `options`
  say otherwise, in front of an unbounded queue. A request waits in
  that queue before any handler, so before `wrap-max-in-flight`, sees
  it. With `virtual-threads?` in `options`, Jetty runs each request on
  a virtual thread of its own, and nothing in Jetty bounds how many run
  at once.
- **Performers.** `message-bus/perform`, in `performers.clj`, runs one
  dispatcher thread that puts each delivery on the queue of the
  performer `(mod (hash k) n)` names, and `n` performers on
  `io-thread`s, each taking from a queue of sixteen and calling
  `run-one`, which acks when the handler returns and nacks when it
  throws. A full queue stops the dispatcher, which stops the backend's
  loop. `performers` is set per entry under
  `kafka/message-bus-consumers` and `pulsar/message-bus-consumers`, and
  as a map from channel to count under `message-bus/local-bus`; `mqtt`
  ignores it.
- **Metrics.** `telemetry/counter`, `inc-counter!` and `add-counter!`,
  in `telemetry`'s `core.clj`, create counters through clj-otel's
  `instrument/instrument`, on an `:otel` instance's meter or the default
  one, and return nil, a no-op, when `:otel` is nil. There is no gauge.
- **Durations.** `util/nanos`, in `utility`'s `time.clj`, reads the
  monotonic clock, and the server's `request-log` interceptor in
  `router.clj` and the command processor measure with it.
  `no-raw-time-id` refuses `System/nanoTime` outside `utility`.
- **Pure functions under a stateful loop.** `kafka`'s offset count, in
  `kafka/offsets.clj`, is a set of pure functions the consumer's polling
  thread folds its acknowledgements through, tested on their own in
  `offsets_test.clj`.

## Proposed Solution

### The brick

`components/concurrency-limit`, with no library: `deps.edn` with empty
`:deps`, and no system component, since the server and `message-bus`
build a limiter from their own config. The files:

- `interface.clj` — the operations below, delegating to `core.clj`.
- `core.clj` — the limiter, permits, and taking and releasing them.
- `window.clj` — the window of samples, as pure functions.
- `gradient.clj` — the algorithm, a pure `step`.
- `options.clj` — the options schema, and normalising an integer or a
  map to one options map.

Operations:

- `limiter` — `(limiter opts)`, where `opts` is a `max-in-flight` value,
  returns a limiter, or an `error/reject` of category
  `:concurrency-limit/invalid-options` whose `:message` names the key
  at fault. An integer `n` is a fixed limit, the options
  `{:initial n :min n :max n}`, which the clamp holds still.
- `try-acquire` — `(try-acquire limiter)` returns a permit, or nil when
  the limit is reached. It never waits.
- `acquire` — `(acquire limiter)` returns a permit, waiting until one is
  free.
- `release` — `(release permit)` records the permit's latency and frees
  it; `(release permit :ignored)` frees it without recording, for a
  handler that threw.
- `limit` and `in-flight` — the current limit, an integer, and the
  number of permits held.
- `options-schema` — the Malli schema of a `max-in-flight` value, which
  the server's and the consumers' config schemas name.

A limiter is an atom holding `:limit` (a double, whose floor is the
number of permits), `:in-flight`, the open window and the algorithm's
state, and a monitor that waiting performers wait on. `try-acquire`
increments `:in-flight` under the limit in one `swap!`, and records the
highest `:in-flight` the window has seen. A permit is the limiter and
the `util/nanos` it was taken at. `release` decrements
`:in-flight`, adds the sample to the window, closes the window and steps
the algorithm where the window is full, all in one `swap!`, then
notifies the monitor. `acquire` tries and waits under the monitor, so no
release between the two is missed.

The options map:

- `initial` — the limit at start. Required in a map.
- `min` — the lowest the limit falls, default 1.
- `max` — the highest it rises, default 1000 on the server, and
  `performers` on a consumer.
- `tolerance` — how far above the floor latency may rise before the
  limit shrinks, as a multiple of it, default 1.5: queueing may add half
  the latency an unloaded process sees.
- `window-size` and `window-ms` — a window closes once it holds
  `window-size` samples (default 10) and has been open `window-ms`
  (default 1000), which sets how fast the limit reacts.
- `floor-windows` — how many windows the floor is the lowest of,
  default 60, so the floor follows a store that has become slower for
  good within a minute.
- `smoothing` — how much of each step's target the limit takes,
  default 0.2.

The schema refuses `min` above `initial` or `initial` above `max`.

### The algorithm

A limit can remove only the latency queueing adds: it cannot make a
store faster than it is unloaded. So the algorithm measures latency
against the floor, the latency an unloaded process sees, and never
against a number in configuration.

A window holds the count of samples, their summed latency and the
highest `:in-flight` while it was open. `gradient/step` is
`(step state window opts)`, returning the state with a new `:limit`,
clamped to `min` and `max`. With a window's mean latency `short`:

1. Add `short` to the state's last `floor-windows` means; the floor is
   the lowest of them.
2. Where the window's highest in-flight is below half the limit, keep
   the limit.
3. Otherwise take `gradient`, `tolerance × floor / short` clamped to
   between 0.5 and 1.0, and `target`, `limit × gradient + √limit`.
4. Set the limit to `limit × (1 − smoothing) + target × smoothing`.

While latency stays within `tolerance` of the floor the gradient is 1,
and the limit grows by `smoothing × √limit` a window; once queueing
pushes latency past it, the limit shrinks in proportion. It settles a little
past `tolerance` times the floor, where the √limit headroom balances
the shrink.

### The server

`shed.clj` replaces the `Semaphore` with a limiter.
`server/wrap-max-in-flight` keeps its arities, and `n` becomes a
`max-in-flight` value, an integer or an options map, from which it
builds the limiter with `concurrency-limit/limiter`. A request takes a
permit with `try-acquire` and is turned away as now when it gets none.
The handler runs inside a `try`/`finally` that releases the permit, as
`:ignored` where the handler threw, which a volatile set after the
handler returns tells apart without a rethrow. A 5xx the handler
returns is a sample like any other response.

`server/jetty-adapter`'s config schema names
`concurrency-limit/options-schema` for `max-in-flight` in place of
`pos-int?`, so `max-in-flight: 1` in `shed-test.yml` means what it means
now. Where `max-in-flight` is set, the adapter's start registers two
gauges on its `telemetry` instance — `mono.server.request.limit` and
`mono.server.request.active`, unit `{request}` — and its stop closes
them before stopping Jetty.

Jetty's threads decide what `max` can mean. On platform threads, a
`max` above `max-threads` is never reached, and a request waiting in
Jetty's queue is neither seen nor measured. On virtual threads, Jetty
bounds nothing and `max-in-flight` is the only bound, so `max` is set
from what is behind the handlers, such as the store's connection pool.

### The performers

`message-bus/perform` takes `:max-in-flight` beside `:performers`. Where
it is set, `perform` builds one limiter for the subscription, with `max`
defaulting to `performers` and an options map's larger `max` refused.
A performer `acquire`s a permit before `run-one` and `release`s it after,
as `:ignored` where the handler threw, and holds its delivery while it
waits, so its queue fills and the dispatcher stops as a full queue stops
it now. Each key goes to the performer it goes to without a limit.

`max-in-flight` sits beside `performers` on each entry under
`kafka/message-bus-consumers` and `pulsar/message-bus-consumers`, carried
on `KafkaConsumer` and `PulsarConsumer` to `perform`, and as a map from
channel to value under `message-bus/local-bus`, as `performers` is. The
files: `performers.clj`, `local.clj` and `system/components.clj` in
`message-bus`; `message_bus.clj` and `system/components.clj` in `kafka`
and in `pulsar`. `mqtt` ignores `max-in-flight` as it ignores
`performers`.

`perform` registers `mono.message_bus.subscription.limit` and
`mono.message_bus.subscription.active`, unit `{message}`, on the
default meter, with the subscription's name — the one its performer
threads carry — as `messaging.consumer.group.name`, and closes them
when its performers stop.

### Gauges

`telemetry/gauge` — `(gauge opts)`, where `opts` carries `:name`,
`:observe`, and optionally `:description`, `:unit` and `:otel` as
`telemetry/counter` takes them — registers an observable gauge whose
callback calls `:observe` and records what it returns, a number or a
sequence of `{:value :attributes}`. It returns the instrument, or nil
where `:otel` is nil. `telemetry/close-instrument` closes it, and takes
nil as a no-op. Both live in `telemetry`'s `core.clj`, behind
`try-nom`.

### Registration

The brick is listed as a `:local/root` dependency in `mono-lib`,
`mono-test-lib` and `realworld-service`, under the qualified key
`com.repldriven.mono.components/concurrency-limit` in the two library
projects; in the root `deps.edn`'s development paths and test paths; and
as a row of the readme's brick table, no library. It is not `:necessary`
in `workspace.edn`, since `server` and `message-bus` depend on it.

### The first slice

1. The brick: options, window, `gradient`, the limiter and its
   operations, and the simulation tests. Nothing calls it yet.
2. The server: `wrap-max-in-flight` on a limiter, the schema change, and
   `telemetry/gauge` with the two server gauges. An integer
   `max-in-flight` behaves as it does now.
3. The performers: `:max-in-flight` through `perform`, the three
   backends' config, and the subscription gauges.

### Tests

- **`concurrency-limit`.** The algorithm and the window as pure
  functions, folded over the latencies of a simulated workload: a
  simulated store serving `c` requests at once at a fixed latency, and
  queueing the rest, offered more load than it can take. The tests
  assert, over the run's sequence of limits, that the limit always stays
  between `min` and `max`; that it settles within a band of `c` and stays
  there; that when the store's latency doubles mid-run the limit falls,
  and once the floor has followed, settles again; that it does not rise
  while the load uses less than half of it; and that an integer never
  moves. The limiter's interface
  test asserts that `try-acquire` refuses at the limit, that `release`
  frees a permit, that `:ignored` leaves the window unchanged, and that
  a waiting `acquire` returns once another permit is released.
- **`server`.** The existing `max-in-flight-test` holds with
  `max-in-flight: 1`. A new test starts the adapter with an options map
  and a handler whose latency the test sets, and asserts that the
  limit falls when that latency rises, that the gauges read the limit,
  and that a probe is never turned away.
- **`message-bus`.** The local backend's performers test binds a channel
  with four performers and `max-in-flight: 2`, and asserts that no more
  than two handlers run at once, that each key's order holds, and that
  a handler that throws leaves the limit as it was.
- **`kafka` and `pulsar`.** Each backend's performers test adds
  `max-in-flight` to its consumer and asserts each key's order and that
  the committed or acknowledged position reaches the end, as without a
  limit.
- **`telemetry`.** A gauge registered on in-memory telemetry is read
  back with the value `:observe` returns, and reads nothing once
  closed.

## Alternatives Considered

- **Resizing a consumer's performers.** Rejected: changing their number
  moves keys between performers while earlier messages wait in the old
  queue, as ADR-0041 says.
- **Wrapping Netflix's concurrency-limits.** Rejected: the limiter and
  one algorithm are smaller than the wrapper brick ADR-0011 would ask
  for. Its gradient algorithm is taken in part: the clamp, the headroom
  and the smoothing.
- **A long-run average as the baseline**, as that gradient algorithm
  uses. Rejected: under sustained overload the average absorbs the
  queueing it should detect, so what `tolerance` allows creeps upward.
- **AIMD on a latency budget.** Rejected: a limit removes only queueing,
  so where a store's unloaded latency exceeds the budget the limit falls
  to `min` and turns away load it cannot speed up; and backing off on a
  window's slowest sample moves the limit on one outlier. A latency
  budget belongs to the client's timeout and to alerting.
- **A `Semaphore` resized as the limit moves.** Rejected: `Semaphore`
  shrinks only through a protected method, and a limit held in the same
  `swap!` as the window and the in-flight count keeps the three
  consistent without one.
- **Queueing a request beyond the server's limit.** Rejected:
  `max-in-flight` turns a request away so the client retries elsewhere,
  and a queue in front of a slow store holds requests until they time
  out.
- **A separate `concurrency-limit` key beside `max-in-flight`.**
  Rejected: two keys for one bound would need a rule for when both are
  set, and an integer `max-in-flight` is already a fixed limit.
- **Step on every sample.** Rejected: one sample's latency is noise, and
  stepping on each one moves the limit as often as requests arrive.

## Known Limitations

- **One limiter per server.** Every route shares the server's limiter,
  so a route slow by nature lowers the limit for all of them. Separate
  limiters per route or per class of request are not designed.
- **Per process.** Each process adapts on its own and shares nothing
  with the others; they agree only through the latency they all see.
- **Fast failures are not seen.** A handler that throws is `:ignored`,
  so contention that shows as a quick conflict rather than a slow write
  does not lower the limit.
- **Jetty's queue is not seen.** On platform threads, a request waits
  in Jetty's unbounded queue before the limiter sees it, so neither its
  wait nor its count reaches the limit. Bounding that queue is Jetty's
  configuration, not this design's.
- **The floor follows slowly.** A store that has become slower for good
  is read as queueing until the faster windows age out of the floor,
  and the limit sits low meanwhile.
- **Streamed bodies are not limited.** A body written after the handler
  returns, such as an SSE stream, holds no permit, as now.
- **Defaults are untested in production.** The tunables' defaults are
  chosen against the simulation; no deployment has measured them.

## References

- [ADR-0041](../adr/0041-concurrency-adapts-to-latency-through-a-limit-not-by-resizing-performers.md)
  — Concurrency adapts to latency through a limit, the decision this
  design serves.
- [message-bus](message-bus.md) — the performers, the queues and the
  dispatcher the consumer's limit sits inside.
- [ADR-0040](../adr/0040-a-consumer-hands-each-message-to-a-performer-chosen-by-its-key.md)
  — A consumer hands each message to a performer chosen by its key, the
  order the limit must not touch.
- [ADR-0005](../adr/0005-error-handling-with-anomalies.md) — Error
  handling with anomalies, for `limiter`'s rejection.
- [ADR-0011](../adr/0011-one-component-per-third-party-library.md) —
  One component per third-party library, why the algorithm is written
  rather than wrapped.
- [system-configurations](../recipes/code/system-configurations.md) —
  the YAML `max-in-flight` is read from, and `!keyword`.
- [test-system](../recipes/test/test-system.md) — `with-test-system`,
  which the server and bus tests drive.
- [Netflix concurrency-limits](https://github.com/Netflix/concurrency-limits)
  — the gradient algorithm this design adapts.
- [Little's law](https://en.wikipedia.org/wiki/Little%27s_law) — the
  relation between throughput, latency and work in flight that a limit
  searches for.
