# Concurrency limit

> **Status: proposal.** The first slice is built: the
> `concurrency-limit` brick, its estimator and its simulation tests
> exist, and nothing calls them yet. What the rest of the design
> changes — `server`'s `wrap-max-in-flight` and its `max-in-flight`
> key, `message-bus`'s performers and each consumer's `performers` key,
> `telemetry`'s counter — exists and is named as such in Background.
> Everything else under Proposed Solution is the build list, and "The
> first slice" says what comes next.

## Objective

A process built on these bricks bounds the work it takes on at once by
a limit it finds by measuring: the most throughput the store behind its
handlers gives, at the least work in flight that gives it. The server
turns away a request beyond the limit, and a consumer runs no more
handlers at once than it allows. One configuration serves every
platform a workspace runs on. This TDD decides the brick that holds the
limit, the estimator that moves it, how the server and the performers
take and release permits, the configuration both read, the gauges that
publish it, and how the estimator is tested.

In scope: the `concurrency-limit` brick — its `interface.clj`, the
limiter and its permits, the window, the estimator and the options
schema; the `max-in-flight` key on `server/jetty-adapter` and on every
consumer that has `performers`; `telemetry/gauge`; registration in
`mono-lib`, `mono-test-lib`, the root `deps.edn` and the readme; and
the tests.

Out of scope: holding the limit at measured capacity rather than fixing
it, and leaving the number of performers alone, decided in
[ADR-0041](../adr/0041-a-dynamic-concurrency-limit-keeps-work-in-flight-at-measured-capacity.md);
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
- `knee.clj` — the estimator, a pure `step`.
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
- `limit`, `in-flight` and `capacity` — the current limit, the number
  of permits held, and the capacity estimate in units a second, nil
  until a window has bound.
- `options-schema` — the Malli schema of a `max-in-flight` value, which
  the server's and the consumers' config schemas name.

A limiter is an atom holding `:limit` (a double, whose floor is the
number of permits), `:in-flight`, the open window and the estimator's
state, and a monitor that waiting performers wait on. `try-acquire`
increments `:in-flight` under the limit in one `swap!`, records the
highest `:in-flight` the window has seen, and marks the window bound
where it refuses. A permit is the limiter and the `util/nanos` it was
taken at. `acquire` tries and waits under the monitor, so no release
between the two is missed, and marks the window bound where it waits.
`release` decrements `:in-flight`, adds the sample to the window,
closes the window and steps the estimator where the window is full, all
in one `swap!`, then notifies the monitor.

The options map, every key optional, so `max-in-flight: {}` is a
dynamic limit with defaults:

- `initial` — the limit at start, default 8, or `max` where lower.
- `min` — the lowest the limit falls, default 1.
- `max` — the highest it rises, and so what bounds the search, default
  1000 on the server and `performers` on a consumer.
- `headroom` — the limit as a multiple of the knee, default 1.5, which
  absorbs a burst at the cost of what waits: at 1 the store holds its
  knee and nothing waits, at 2 as much again waits.
- `windows` — how many windows the estimates are taken over, default
  10.
- `window-size` and `window-ms` — a window closes once it holds
  `window-size` samples (default 10) and has been open `window-ms`
  (default 1000), which sets how fast the limit moves.

The schema refuses `min` above `initial`, `initial` above `max`, and
`headroom` below 1. A `window-ms` of 0 closes a window on its count
alone.

### The estimator

A window holds the count of samples, their summed latency, the
`util/nanos` it opened at, the highest `:in-flight` while it was open,
and whether it bound. Its throughput is its count over the time it was
open. `knee/step` is `(step state window opts)`: it appends the window
to the state's last `windows` windows and returns the state with a new
`:limit`, clamped to `min` and `max`. From those windows:

- `capacity` is the highest throughput among the windows that bound,
  or none where no window bound;
- `floor` is the lowest mean latency among all of them;
- `knee` is `capacity × floor`, the work in flight that gives capacity
  with nothing waiting.

The state is searching or holding:

1. **Searching**, at start and whenever no window in the last `windows`
   bound. After a window that bound, the limit doubles, unless the
   window's throughput is under 1.25 times the capacity before it, in
   which case the throughput has stopped rising and the state holds. A
   window that did not bind leaves the limit as it is.
2. **Holding** runs a cycle of eight windows, starting at the first:
   - one drain window, at `0.75 × knee`, so what waits drains and the
     floor is measured unloaded;
   - six windows at `headroom × knee`;
   - one probe window, at `1.25 × headroom × knee`, which finds capacity
     that has grown, since a window that delivers more raises
     `capacity`.

A window that did not bind changes no estimate, so quiet traffic never
lowers capacity; once the last window that bound ages out of `windows`,
the state searches again from the limit it holds. A limit that starts
above the knee measures a floor with work waiting; each drain window
lowers the floor towards the unloaded latency, and the limit with it.

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
now. Where `max-in-flight` is set, the adapter's start registers three
gauges on its `telemetry` instance — `mono.server.request.limit` and
`mono.server.request.active`, unit `{request}`, and
`mono.server.request.capacity`, unit `{request}/s` — and its stop
closes them before stopping Jetty.

Jetty's threads decide what the limit can reach. On platform threads, a
limit above `max-threads` is never reached, and a request waiting in
Jetty's queue is neither seen nor measured, so a dynamic limit wants
`virtual-threads?` or `max-threads` above `max`. On virtual threads,
Jetty bounds nothing, and `max` bounds the search.

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

`perform` registers `mono.message_bus.subscription.limit`,
`mono.message_bus.subscription.active` and
`mono.message_bus.subscription.capacity`, units `{message}` and
`{message}/s`, on the default meter, with the subscription's name — the
one its performer threads carry — as `messaging.consumer.group.name`,
and closes them when its performers stop.

### Configuring a limit

`max-in-flight` takes the same shapes on the server and on a consumer:

```yaml
# fixed: initial, min and max are all 200
max-in-flight: 200

# dynamic, every key defaulted
max-in-flight: {}

# dynamic, starting where a fixed limit stood
max-in-flight: {initial: 200}
```

No key names a throughput or a latency. `initial` is where the search
starts, so a fixed limit that works is a good one; `max` bounds the
search; `headroom` trades waiting for burst room and leaves throughput
as it is.

The examples below run on virtual threads with the defaults, offered
more than the store can serve unless they say otherwise. A store's
capacity is the requests it serves at once times one over its unloaded
latency, and with more than that in flight a request waits its turn.

- **Replacing a fixed 200, on a larger platform.** The store serves 300
  requests at once at 50 ms, so 6000 a second, and its knee is 300.
  A fixed 200 serves 4000 a second at 50 ms and turns the rest away.
  With `{initial: 200}`, the first window binds at 4000 a second, so
  the limit doubles to 400; that window gives 6000, more than 1.25 times
  4000, so it doubles to 800; that one gives 6000 again, so the state
  holds. It drains at 225, then holds at 450, serving 6000 a second at
  75 ms: half as much again as the fixed 200, for 25 ms more.
- **The same configuration, on a smaller platform.** The store serves
  120 at once at 50 ms, so 2400 a second, and its knee is 120. The first
  window binds at 2400 a second with 200 in flight, at 83 ms; doubling
  to 400 gives 2400 again, so the state holds at a knee of 200, from a
  floor measured with 80 waiting. The first drain, at 150, measures
  62.5 ms and brings the knee to 150; the second, at 112, measures the
  unloaded 50 ms and brings it to 120. Within two cycles the limit holds
  at 180, serving 2400 a second at 75 ms.
- **A ramp.** On the larger platform, load rises from nothing to 8000 a
  second over a minute. The limit of 200 binds as load passes 4000 a
  second and doubles to 400 that window; once load passes 6000 work
  waits, the limit binds again, doubles to 800, finds throughput flat,
  and holds at 450. Requests are turned away below capacity only in the
  window in which load crossed 200.
- **The store slows.** On the larger platform each request slows to
  100 ms, so the store serves 3000 a second. Capacity halves and the
  floor doubles, so the knee stays 300 and the limit 450, serving 3000 a
  second at 150 ms. For a few windows, while the old windows age out,
  capacity is still the old one and the floor is the old held latency,
  so the limit is 675 before it settles at 450 within a cycle.
- **Quiet traffic.** At 1000 a second about 50 are in flight, the limit
  never binds, and it stays where it was. Once no window in `windows`
  has bound, the state searches again, so a burst past the limit
  doubles it within a window.
- **Headroom.** On the larger platform, `headroom: 1` holds at 300 and
  50 ms, and `headroom: 2` at 600 and 100 ms; both serve 6000 a second.
  At 1 nothing waits, so a burst above the knee is turned away rather
  than queued at the store.
- **A consumer.** Sixteen performers whose handlers write to a store
  that takes 4 writes at once at 20 ms, so 200 a second:

  ```yaml
  command:
    consumer: !system/local-ref consumers.command
    performers: 16
    max-in-flight: {}
  ```

  The limit starts at 8 and binds at 200 a second, at 40 ms; doubling
  to 16, which is `max`, gives 200 again, so the state holds. Its first
  drain measures 30 ms and its second the unloaded 20 ms, so within two
  cycles it holds at 6: four handlers writing and two waiting, at
  30 ms. The other ten performers hold their next delivery, their queues
  fill, and the backend's loop waits. Every key stays with its
  performer.

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

1. Built: the brick — options, window, the estimator, the limiter and
   its operations, and the simulation tests. Nothing calls it yet.
2. The server: `wrap-max-in-flight` on a limiter, the schema change, and
   `telemetry/gauge` with the three server gauges. An integer
   `max-in-flight` behaves as it does now.
3. The performers: `:max-in-flight` through `perform`, the three
   backends' config, and the subscription gauges.

### Tests

- **`concurrency-limit`.** The estimator and the window as pure
  functions, folded over a simulated workload: a store serving `c`
  units at once at an unloaded latency, waiting the rest in turn,
  offered a load the test shapes. Over each run's sequence of limits
  the tests assert that the limit always stays between `min` and `max`;
  that under overload it holds at `headroom` times the store's knee and
  throughput stays within a few percent of capacity; that a ramp is
  turned away below capacity only in the windows in which it crossed
  the limit; that when every request slows the limit stays; that
  capacity raised mid-run is found by the probe windows; that a limit
  starting above the knee settles at it within a few cycles; that quiet
  traffic moves nothing; and that an integer never moves. The limiter's
  interface test asserts that `try-acquire` refuses at the limit and
  marks the window bound, that `release` frees a permit, that
  `:ignored` leaves the window unchanged, and that a waiting `acquire`
  returns once another permit is released.
- **`server`.** The existing `max-in-flight-test` holds with
  `max-in-flight: 1`. A new test starts the adapter with
  `max-in-flight: {}` and a handler that serves a set number at once at
  a set latency, offers it more, and asserts that the limit rises past
  `initial` and holds near `headroom` times the knee, that the gauges
  read the limit and capacity, and that a probe is never turned away.
- **`message-bus`.** The local backend's performers test binds a channel
  with four performers and `max-in-flight: 2`, and asserts that no more
  than two handlers run at once, that each key's order holds, and that
  a handler that throws moves no estimate.
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
- **The gradient algorithm of Netflix's concurrency-limits.** Rejected:
  it moves the limit by latency against a tolerance, so it settles past
  the knee with work waiting, and grows by a fraction of √limit a
  window, so a ramp outruns it. Taken in part: the window of samples
  and the clamp.
- **AIMD on a latency budget.** Rejected: where a store's unloaded
  latency exceeds the budget the limit falls to `min` and turns away
  load it cannot speed up, and the budget is a number for each
  platform.
- **Leaving the search on a rise in latency.** Rejected: telling a rise
  from noise needs a threshold, which is a number for each platform; a
  plateau in throughput is the knee itself.
- **Pacing, as BBR paces packets.** Rejected: the limiter bounds what is
  in flight and turns the rest away, and spacing requests out would
  queue them in front of the handler.
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

- **The drain costs throughput.** One window in eight runs at
  three-quarters of the knee, about three percent of throughput
  overall.
- **The search overshoots.** It finds throughput flat only after
  doubling past the knee, so for a window the store has up to twice the
  knee in flight.
- **The estimates age apart.** Capacity and floor come from the same
  windows but not the same window, so for up to a cycle after the store
  changes the limit is too low or too high.
- **One limiter per server.** Every route shares the server's limiter,
  so a route slow by nature moves the estimates for all of them.
  Separate limiters per route or per class of request are not designed.
- **Per process.** Each process estimates on its own and shares nothing
  with the others; they agree only through what the store gives each.
- **Fast failures are not seen.** A handler that throws is `:ignored`,
  so contention that shows as a quick conflict moves no estimate.
- **Jetty's queue is not seen.** On platform threads, a request waits
  in Jetty's unbounded queue before the limiter sees it, so neither its
  wait nor its count reaches the estimates.
- **Streamed bodies are not limited.** A body written after the handler
  returns, such as an SSE stream, holds no permit, as now.
- **Defaults are untested in production.** The constants and defaults
  are chosen against the simulation; no platform has measured them.

## References

- [ADR-0041](../adr/0041-a-dynamic-concurrency-limit-keeps-work-in-flight-at-measured-capacity.md)
  — A dynamic concurrency limit keeps work in flight at measured
  capacity, the decision this design serves.
- [message-bus](message-bus.md) — the performers, the queues and the
  dispatcher the consumer's limit sits inside.
- [ADR-0040](../adr/0040-a-consumer-hands-each-message-to-a-performer-chosen-by-its-key.md)
  — A consumer hands each message to a performer chosen by its key, the
  order the limit must not touch.
- [ADR-0005](../adr/0005-error-handling-with-anomalies.md) — Error
  handling with anomalies, for `limiter`'s rejection.
- [ADR-0011](../adr/0011-one-component-per-third-party-library.md) —
  One component per third-party library, why the estimator is written
  rather than wrapped.
- [system-configurations](../recipes/code/system-configurations.md) —
  the YAML `max-in-flight` is read from.
- [test-system](../recipes/test/test-system.md) — `with-test-system`,
  which the server and bus tests drive.
- [BBR: Congestion-Based Congestion Control](https://queue.acm.org/detail.cfm?id=3022184)
  — the estimate of bottleneck bandwidth and round-trip time, the
  search, and the probe and drain cycle this design adapts.
- [Netflix concurrency-limits](https://github.com/Netflix/concurrency-limits)
  — the gradient algorithm considered and rejected.
- [Little's law](https://en.wikipedia.org/wiki/Little%27s_law) — the
  relation between throughput, latency and work in flight that puts the
  knee at capacity times unloaded latency.
