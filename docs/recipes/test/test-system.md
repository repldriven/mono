# Test systems

<!-- tessl-plugin: idioms -->

## Problem

You want a test to boot a system from its YAML configuration, drive it,
and tear it down — without fixtures, global state, or a run that stalls
under Docker.

## Solution

The `test-system` brick provides two macros, and the runner mono ships
runs everything in parallel; two environment variables bound what that
boots.

### with-test-system

`with-test-system` starts a test system from a YAML config, asserts it
started cleanly, and stops it after the body — no `use-fixtures`
ceremony, no `try`/`finally`, no global state.

```clojure
;; Simple form
(with-test-system
  [sys "classpath:my-component/application-test.yml"]
  (let [component (system/instance sys [:path :to :component])]
    ;; test body
    ))

;; With patch-fn — inject a handler or override a component
;; before the system starts
(with-test-system
  [sys ["classpath:server/application-test.yml"
        (fn [defs] (assoc-in defs [:system/defs :server :handler] app))]]
  ;; test body
  )
```

The optional second element of the binding vector is a patch-fn
applied to the system defs before start. Use it to inject HTTP handlers
— analogous to base-level required-component injection; see
[system-configurations.md](../code/system-configurations.md) — or to
swap a component for a test double.

### nom-test>

`nom-test>` chains operations as let-style bindings, failing fast on
any anomaly and asserting no anomaly occurred. Use `_` for bindings
whose values are only needed for `is` assertions:

```clojure
(nom-test> [result1 (operation1)
            _       (is (= expected result1))
            result2 (operation2 result1)
            _       (is (some? result2))])
```

For a single anomaly check with no further bindings:

```clojure
(nom-test> [_ (operation-that-must-not-fail)])
```

See [error-handling.md](../code/error-handling.md) for the broader
anomaly story.

### Test resources

Each brick that boots a system in tests has its own
`test-resources/<brick>/application-test.yml`. Shared test
configuration (container groups, common schemas) lives in the
`test-resources` brick, which a brick's `:test` alias puts on the
classpath.

The classpath URL pattern `classpath:<brick>/application-test.yml` is
what `with-test-system` expects; load mechanics are covered by
[system-configurations.md](../code/system-configurations.md).

### Running tests

```bash
clojure -M:poly test project:dev
clojure -M:poly test brick:<brick-name> project:dev :all
```

`just test` runs every brick in every project, capped as below. No test
recipe starts Docker; `just start-docker` does, once.

### The runner's parallelism, and what bounds it

The test runner is eftest, run out of process by
`external-test-runner`. It runs namespaces in parallel, and the vars
within each namespace in parallel on a pool sized by the JVM's
processor count. Two settings bound what that boots:

- `with-test-system` holds one of `TEST_SYSTEM_PERMITS` permits from
  start to stop, so at most that many test systems, each with its own
  containers, are up at once in the JVM. Unset, nothing waits.
- `JDK_JAVA_OPTIONS=-XX:ActiveProcessorCount=<n>` caps every pool that
  sizes itself from the processor count.

`just test` caps the JVM to Docker's CPU count. A raw
`clojure -M:poly test` needs the cap set the same way, and a run that
boots more systems than the Docker VM can hold needs the permits set
too, or the run stops making progress without failing.

`^:eftest/synchronized` on a namespace runs that file's vars one at a
time. It is for a file whose tests share state, such as a `with-redefs`
of one var across tests or a global HTTP fake, and not for a file that
boots infrastructure, which the permit bounds:

```clojure
(ns ^:eftest/synchronized
  com.repldriven.mono.realworld-store.interface-test
  ...)
```

## Rules

**MUST:**

- Manage system lifecycle in tests with `with-test-system`.
- Use `nom-test>` for assertions over anomaly-returning calls.
- Place per-brick test config at
  `test-resources/<brick>/application-test.yml`.
- Set `TEST_SYSTEM_PERMITS` and the processor cap as `just test` does
  when running `clojure -M:poly test` directly against a Docker VM
  with fewer CPUs than the host.
- Mark a namespace whose tests share state, such as a `with-redefs`,
  with `^:eftest/synchronized`; a namespace that only boots
  infrastructure carries no marker.
- Inject a collaborator rather than `with-redefs` a var another
  namespace calls: the redefinition is JVM-wide, and namespaces run in
  parallel whatever the marker says.

**MUST NOT:**

- Use `use-fixtures` for system lifecycle.
- Mark a namespace `^:eftest/synchronized` to bound how many systems
  it boots; the permit does that, and the marker only slows the file.

## Discussion

`with-test-system` over `use-fixtures` is a deliberate choice. Fixtures
encourage hidden global state, are surprisingly hard to compose, and
don't play well with anomaly-returning startup code. Explicit
`with-test-system` makes lifecycle visible at every test, composes with
`nom-test>`, and supports patch-fns for handler injection cleanly.

The permit is about resource starvation. Spinning up ten container-
backed systems at once doesn't make tests faster — it makes them
flakier, and against a Docker VM with fewer CPUs than the JVM sees the
run stops making progress without failing. A bound taken where the
system is booted counts exactly what needs bounding, whatever the
runner's mode. The synchronized marker serialises a file's vars, which
only shared state needs; a file marked to bound its boots runs slower
for nothing. It serialises nothing beyond that file: a `with-redefs` of
a var another namespace calls, an interface fn a handler reaches, is
visible to that namespace's tests while they run alongside, and the
marker cannot stop it. An injected collaborator can.

## References

- [error-handling.md](../code/error-handling.md)
- [system-configurations.md](../code/system-configurations.md)
- [testcontainers.md](testcontainers.md)
- `test-system` brick (provides `with-test-system`, `nom-test>`)
- `test-resources` brick (shared container groups and schemas)
