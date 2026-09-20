# System components

<!-- tessl-plugin: design -->

## Problem

You want to register a brick's components and group for use in system
configurations.

## Solution

We use `system/defcomponents` to register a brick's runtime components
— instances with start/stop lifecycle — under a named group. A
[system configuration](system-configurations.md) references each by its
`<group>/<component>` keyword, and donut.system resolves them at start
time.

The call lives in a `system.clj` (single namespace) or a
`system/core.clj` (when there are multiple definition namespaces). The
brick's `interface.clj` bare-requires that namespace, using the
bracketed unaliased form, so the multimethods are extended whenever the
brick is loaded.

### Defining a component

A component is a map with a few standard keys:

```clojure
(def http-url
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (server-jetty/http-local-url (:jetty-adapter config))))
   :system/config {:jetty-adapter system/required-component}
   :system/instance-schema string?})
```

- `:system/start` — fn returning the instance. Receives a map with
  `config` (resolved from YAML) and `instance` (any prior instance from
  a hot-reload). Pattern: `(or instance ...)` to preserve instances
  across reloads.
- `:system/stop` — optional, fn that closes/releases the instance.
- `:system/config` — map of expected config keys. Use
  `system/required-component` to mark slots that must come from YAML
  (or be injected by the caller).
- `:system/config-schema` — optional Malli schema validating the
  resolved config.
- `:system/instance-schema` — predicate or Malli schema validating the
  started instance.

### Simple pattern: `system.clj`

When a brick has one cluster of component definitions, put them all in
`system.clj`. The `defcomponents` call at the bottom groups them under a
keyword group name.

Adapted from `components/server/.../system.clj`:

```clojure
(ns com.repldriven.mono.server.system
  (:require
    [com.repldriven.mono.server.jetty :as server-jetty]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]
    [ring.adapter.jetty9 :as jetty]))

(def interceptors
  {:system/start ...
   :system/config nil
   :system/instance-schema vector?})

(def jetty-adapter
  {:system/start ...
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance) (.stop instance)))
   :system/config {:handler system/required-component
                   :interceptors nil
                   :ready-fn nil
                   :options default-jetty-adapter-options}
   :system/config-schema [:map [:handler fn?]]
   :system/instance-schema some?})

(system/defcomponents :server
                      {:interceptors interceptors
                       :jetty-adapter jetty-adapter
                       :http-url http-url})
```

The brick's `interface.clj` bare-requires the system namespace:

```clojure
(ns com.repldriven.mono.server.interface
  (:require
    [com.repldriven.mono.server.system]
    [com.repldriven.mono.server.core :as core]))
```

### Structured pattern: `system/` folder

When a brick has two or more clusters of component definitions, split
them across files in a `system/` folder. The actual definitions live in
one or more files (typically `components.clj`); a `system/core.clj`
imports them and makes a single `defcomponents` call aggregating
everything.

From `components/kafka/.../system/core.clj`:

```clojure
(ns com.repldriven.mono.kafka.system.core
  (:require
    [com.repldriven.mono.kafka.system.components :as components]

    [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :kafka
                      {:bootstrap-servers components/bootstrap-servers
                       :admin components/admin
                       :topics components/topics-component
                       :producer components/producer-component
                       :producers components/producers
                       :consumer components/consumer-component
                       :consumers components/consumers
                       :message-bus-producers components/message-bus-producers
                       :message-bus-consumers components/message-bus-consumers})
```

Layout:

```
components/<brick>/
  src/com/repldriven/mono/<brick>/
    system/
      core.clj         ; aggregates and calls defcomponents
      components.clj   ; the component definition maps
      ...              ; further files if needed
```

The brick's `interface.clj` bare-requires `system.core`:

```clojure
(ns com.repldriven.mono.<brick>.interface
  (:require
    [com.repldriven.mono.<brick>.system.core]
    [com.repldriven.mono.<brick>.core :as core]))
```

### Choosing between the patterns

- One cluster of definitions → `system.clj`.
- Two or more clusters → `system/` folder.

Don't promote a `system.clj` into a folder until you actually have a
second cluster.

### Naming shared resource components

Don't bake an environment name (`prod`, `dev`, `staging`, `demo`) into
a shared resource component or its config. Name the component by the
concern it represents:

- OK: `resources`, `infra-resources`.
- Not OK: `prod-resources`, `staging-resources`.

The same artefact gets deployed to multiple environments. An
env-in-the-name component reads as "this is only valid in that
environment", which encourages duplicate components when a second
environment shows up. Discriminate environments via env vars and
deployment values, not via component names. A `*-test-resources`
exception is fine — `test` there is the runtime mode, not a deployment
environment.

### Bundling system requires for tests

A base or project whose tests need many bricks' components extended
consolidates the bare requires into a single `system.clj` in its test
source tree. Test namespaces require *that* file rather than listing
every bare require themselves.

```
bases/<base>/
  test/com/repldriven/mono/<base>/
    system.clj            ; consolidates all bare requires
    routes_test.clj       ; (:require [...<base>.system])
```

The test `system.clj` namespace:

```clojure
(ns com.repldriven.mono.<base>.system
  (:require
    [com.repldriven.mono.jdbc.interface]
    [com.repldriven.mono.message-bus.interface]
    [com.repldriven.mono.migrator.interface]
    [com.repldriven.mono.server.interface]
    [com.repldriven.mono.testcontainers.interface]
    ;; ...
    ))
```

Test files require it as a single bare require, alongside any aliased
requires they need for actual use:

```clojure
(ns com.repldriven.mono.<base>.routes-test
  (:require
    [com.repldriven.mono.<base>.system]
    [com.repldriven.mono.jdbc.interface :as jdbc]
    [clojure.test :refer [deftest is testing]]))
```

A component is welcome to appear both bare (in the bundle) and aliased
(where the test calls it). The bare require extends the system
multimethods; the aliased require lets the test call functions on it.

## Rules

**MUST:**

- System component definitions are registered through `defcomponents`
  from `system.clj` (single namespace) or a `system/` folder with
  `system/core.clj` aggregating (multiple namespaces).
- Bare requires use the bracketed unaliased form
  (`[com.repldriven.mono.x.system]`).
- A brick's `interface.clj` bare-requires the system namespace so
  multimethods are extended on load.
- A configuration that names a brick's kinds is built only after that
  brick's `interface.clj` is loaded: `system/defs` refuses a kind no
  `defcomponents` registered as `:system/unknown-component-kind`, rather
  than building a component nothing starts whose config would stand in
  as its instance.
- Tests in a base or project consolidate system-component bare
  requires into a single `test/.../system.clj` namespace; test files
  require that namespace rather than listing the bricks individually.

**MUST NOT:**

- Call `defcomponents` directly from `interface.clj` (use `system.clj`
  or `system/core.clj`).
- Use the unbracketed bare-require form.
- Bake an environment name (`prod`, `dev`, `staging`) into a shared
  resource component or config. Name by concern (e.g. `resources`).

**SHOULD:**

- Use the simple `system.clj` pattern when a brick has one
  defcomponents namespace; switch to a `system/` folder when there are
  two or more.

## Discussion

The two-pattern split exists because most bricks have one tight cluster
of definitions (simple), but some have several. The `kafka` brick has
the broker's admin, topics, producers and consumers, and the
message-bus records built from them; forcing all that into one file
gets unwieldy. Forcing a folder when there's one entry is overkill.

The test `system.clj` consolidation cuts duplication. When a dozen test
files all need the same bricks extended with system multimethods,
listing the same bare requires in each is busywork; bundling them into
one namespace and requiring that turns it into a single line per test.
A component may also be aliased in the test for actual use — the two
requires don't conflict, they serve different purposes.

## References

- [ADR-0007](../../adr/0007-system-as-data.md) — System as data
- [components.md](components.md)
- [bases.md](bases.md)
- [system-configurations.md](system-configurations.md)
- [donut.system](https://github.com/donut-party/system)
