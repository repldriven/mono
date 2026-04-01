---
theme: catppuccin-macchiato
title: "mono: Systems as Data"
info: |
  A walkthrough of how mono defines, externalises, and boots
  a real Clojure system using donut.system and a YAML reader.
highlighter: shiki
lineNumbers: false
---

# Systems as data

## How mono uses `donut.system` to build a system from configuration data

<div class="mt-12 opacity-70 text-sm">
  kjothen &middot; mono &middot; 31 march 2026
</div>

---
zoom: 0.85
---

# About donut.system

<div class="text-4xl font-medium my-4">

> "donut.system is a data-driven architecture toolkit for Clojure applications"

</div>

<div class="opacity-60 text-sm mb-4">github.com/donut-party/system</div>

Your system is a **map**. Components are nested inside groups.

<v-click>

```clojure
{:server {:jetty-adapter {::ds/start  start-fn
                          ::ds/stop   stop-fn
                          ::ds/config {:port 8080}}}}
```

</v-click>

<v-click>

**Signals** flow through a dependency graph:

| Signal   | Order                        |
| -------- | ---------------------------- |
| `:start` | reverse-topsort (deps first) |
| `:stop`  | topsort (dependents first)   |

</v-click>

<v-click>

**Refs** wire components together — no manual injection:

```clojure
(ds/ref [:fdb :record-db])    ; cross-group
(ds/local-ref [:handler])     ; same group
```

</v-click>

---

# Bookmark example

Three groups. Eight components. One dependency chain.

```
fdb/container
  └─▶ fdb/cluster-file-path
        └─▶ fdb/record-db ────────────────┐
              fdb/bookmark-store ─────────┤
                                          ▼
                              example-bookmark/store
                                          │
                                          ▼
                              server/interceptors
                              server/handler ───────┐
                                                    ▼
                                         server/jetty-adapter
```

<v-click>

`fdb` = FoundationDB via Testcontainers

`example-bookmark` = domain layer

`server` = Jetty via Ring

</v-click>

---
zoom: 0.75
---

# donut.system definition - native form

<div class="grid grid-cols-2 gap-4">

<div>

```clojure
(def system
  {::ds/defs
   {:fdb
    {:container
     {::ds/start  start-fdb-container
      ::ds/stop   stop-fdb-container
      ::ds/config {:image-name "mono/foundationdb:7.3.75"}}

     :cluster-file-path
     {::ds/start  extract-cluster-file-path
      ::ds/config {:container (::ds/local-ref [:container])}}

     :record-db
     {::ds/start  open-record-db
      ::ds/stop   close-record-db
      ::ds/config {:cluster-file-path (::ds/local-ref [:cluster-file-path])}}

     :bookmark-store
     {::ds/start  build-bookmark-store
      ::ds/config {:descriptor  "...ExampleSchemaProto"
                   :record-types {"bookmarks"
                    {"record-type" "Bookmark"
                     "indexes"
                     [{"name"    "tag_idx"
                       "field"   "tags"
                       "fan-out" true}]}}}}}

    :example-bookmark
    {:store
     {::ds/start  build-store
      ::ds/config {:record-db    (::ds/ref [:fdb :record-db])
                   :record-store (::ds/ref [:fdb :bookmark-store])}}}}})
```

</div>

<div>

```clojure
    :server
    {:handler
     {::ds/start build-handler}

     :interceptors
     {::ds/start  build-interceptors
      ::ds/config {:bookmark-store
                   (ds/ref [:example-bookmark :store])}}

     :jetty-adapter
     {::ds/start  start-jetty
      ::ds/stop   stop-jetty
      ::ds/config {:handler      (ds/local-ref [:handler])
                   :interceptors (ds/local-ref [:interceptors])
                   :options      {:join? false :port 8080}}}}}
```

</div>
</div>

---

# What's noticeable here?

```clojure {1-3|5-7|9-11|13-15|all}
;; Start/stop fns live in the data:
::ds/start  start-fdb-container
::ds/stop   stop-fdb-container

;; Config and instance schema live here too:
::ds/config-schema [:map [:image-name string?]]
::ds/instance-schema some?

;; The system map usually lives as source (.clj)
;; Ops can't touch it without touching Clojure/EDN.
;; Environment-specific values are baked in.
```

<v-click>

What if the **implementation** stayed in Clojure, and the **wiring** lived in a file?

</v-click>

---

# How mono uses donut.system: A YAML example

Systems are expressed as either YAML or EDN, whichever you prefer - we'll use a YAML example throughout because it's more familiar to non-Clojure engineers (think Java Spring Boot)

<div class="grid grid-cols-2 gap-8">

<div>

**donut.system EDN**

```clojure
{::ds/defs
 {:fdb              { ... }
  :example-bookmark { ... }
  :server           { ... }}}
```

</div>

<div>

**mono.system YAML**

```yaml
system:
  fdb:
    container: ...
    cluster-file-path: ...
    record-db: ...
    bookmark-store: ...
  example-bookmark:
    store: ...
  server:
    handler: ...
    interceptors: ...
    jetty-adapter: ...
```

</div>
</div>

<v-click>

The hierarchy is identical. The `system:` top-level key scopes a system. Now we need to fill in each component entry.

</v-click>

---

# Declare, don't specify

Key abstraction: Mono separates **what** we want, from **how** it's implemented

<div class="grid grid-cols-2 gap-8">

<div>

**What** — mono.system configuration, data only

```yaml
fdb:
  container: !system/component
    system/component-kind: fdb/container
    image-name: mono/foundationdb:7.3.75
```

</div>

<div>

**How** — donut.system declaration and abstraction

```clojure
(system/defcomponents :fdb
  {:container
   {:system/start  start-fdb-container
    :system/stop   stop-fdb-container
    :system/config
    {:image-name system/required-component}
    :system/instance-schema some?}})
```

</div>

</div>

<v-click>

`!system/component`: resolves values of `system/component-kind` keys to component groups
`system/defcomponents`: registers components with their start/stop fns and config/instance schemas

</v-click>

<v-click>

Note: `!` is the YAML equivalent to Clojure's EDN reader tag literal `#`.

</v-click>

---

# A complete fdb configuration

Plain config drops straight in. String-keyed maps use `!strs`.

```yaml {2-3|4-6|7-9|10-19|all}
fdb:
  container: !system/component
    system/component-kind: fdb/container
  cluster-file-path: !system/component
    system/component-kind: fdb/cluster-file-path
    container: !system/local-ref container
  record-db: !system/component
    system/component-kind: fdb/record-db
    cluster-file-path: !system/local-ref cluster-file-path
  bookmark-store: !system/component
    system/component-kind: fdb/store
    descriptor: com.repldriven.mono.example_schemas.schemas.ExampleSchemaProto
    record-types: !strs
      bookmarks:
        record-type: Bookmark
        indexes:
          - name: tag_idx
            field: tags
            fan-out: true
```

<v-click>

`!strs` forces string keys on the subtree - needed here because protobuf record-type config expects `{"bookmarks" {...}}`, not keyword keys.

</v-click>

---

# Component references in mono YAML

Two ref forms. Dot notation for cross-group, bare name for local.

<div class="grid grid-cols-2 gap-8">

<div>

**donut.system configuration**

```clojure
;; Cross-group
(ds/ref [:fdb :record-db])

;; Same group
(ds/local-ref [:handler])
```

</div>

<div>

**mono.system configuration**

```yaml
# Cross-group
record-db: !system/ref fdb.record-db

# Same group
handler: !system/local-ref handler
```

</div>
</div>

<v-click>

The reader resolves these back to `donut.system/ref` and `donut.system/local-ref` vectors before the system map is assembled.

</v-click>

<v-click>

```yaml
example-bookmark:
  store: !system/component
    system/component-kind: example-bookmark/store
    record-db: !system/ref fdb.record-db
    record-store: !system/ref fdb.bookmark-store
```

</v-click>

---
zoom: 0.94
---

# Complete mono.system declaration

<div class="grid grid-cols-2 gap-4">

<div>

```yaml
system:
  fdb:
    container: !system/component
      system/component-kind: fdb/container
      image-name: mono/foundationdb:7.3.75
    cluster-file-path: !system/component
      system/component-kind: fdb/cluster-file-path
      container: !system/local-ref container
    record-db: !system/component
      system/component-kind: fdb/record-db
      cluster-file-path: !system/local-ref cluster-file-path
    bookmark-store: !system/component
      system/component-kind: fdb/store
      descriptor: mono.schemas.ExampleSchemaProto
      record-types: !strs
        bookmarks:
          record-type: Bookmark
          indexes:
            - name: tag_idx
              field: tags
              fan-out: true
```

</div>

<div>

```yaml
example-bookmark:
  store: !system/component
    system/component-kind: example-bookmark/store
    record-db: !system/ref fdb.record-db
    record-store: !system/ref fdb.bookmark-store

server:
  handler: !system/required-component
  interceptors: !system/component
    system/component-kind: server/interceptors
    bookmark-store: !system/ref example-bookmark.store
  jetty-adapter: !system/component
    system/component-kind: server/jetty-adapter
    handler: !system/local-ref handler
    interceptors: !system/local-ref interceptors
    options:
      port: !profile
        default: 0
        dev: 8080
```

</div>
</div>

---
zoom: 1.0
---

# Important tag literals

<v-click>

**`!system/required-component`** - marks a component that must be provided externally

```yaml
server:
  handler: !system/required-component
```

```clojure
;; Caller must inject it:
(nom-> (env/config config-file profile)
       system/defs
       (assoc-in [:system/defs :server :handler] api/app)
       system/start)
```

</v-click>

<v-click>

**`!profile`** - selects a value by active profile at load time, using `aero`

```yaml
options:
  port: !profile
    dev: 8080
    default: 0
```

`aero` tag literals are supported by default when using EDN config, and the most popular are available if using YAML instead.

</v-click>

---
zoom: 0.85
---

# Implementing and registering components

Implementation is registered once, referenced by kind everywhere.

```clojure {1-4|5-14|16-17|all}
(ns com.repldriven.mono.example-bookmark.system
  (:require
    [com.repldriven.mono.system.interface :as system]))

(def ^:private bookmark-store
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance config))

   :system/config
   {:record-db    system/required-component
    :record-store system/required-component}

   :system/instance-schema map?})

(system/defcomponents :example-bookmark
  {:store bookmark-store})
```

<v-click>

`defcomponents` registers `:example-bookmark/store` as a known kind. The YAML's `system/component-kind: example-bookmark/store` resolves to this definition at load time.

</v-click>

<v-click>

Config keys declared in `:system/config` are validated against what YAML supplies. `system/required-component` values must be present - either from YAML or injected by the caller. This is all possible from out-the-box support in `donut.system`.

</v-click>

---
zoom: 0.85
---

# Starting the system

```clojure {1-6|all}
(defn start
  [config-file profile]
  (nom-> (env/config config-file profile)
         system/defs
         (assoc-in [:system/defs :server :handler] api/app)
         system/start))
```

<v-click>

- `env/config` loads and parses the YAML, resolving `!profile` tags for the given profile
- `system/defs` lifts the parsed config into a donut system map
- `assoc-in` injects `server/handler` - the `!system/required-component` slot from the YAML
- `nom->` threads through, short-circuiting on any anomaly

</v-click>

<v-click>

donut.system then walks the **reverse-topsort** graph - dependencies start first:

```
fdb/container → fdb/cluster-file-path → fdb/record-db
  fdb/bookmark-store → example-bookmark/store
    → server/interceptors + server/handler
      → server/jetty-adapter  ✓
```

</v-click>

---
zoom: 0.85
---

# Stopping the system

```clojure
(defn stop [system] (system/stop system))
```

<v-click>

Signals flow in **topsort** order - dependents stop before dependencies:

```
server/jetty-adapter  ✓
  → fdb/record-db     ✓
    → fdb/container   ✓
```

Components without a `:system/stop` fn are skipped silently.

</v-click>

<v-click>

At the REPL, the full lifecycle:

```clojure
(ns dev.example-api
  (:require
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.example-api.main :as main]))

(comment
  (def sys (main/start "classpath:example-api/application-test.yml" :dev))
  (tap> sys)
  (main/stop sys))
```

`classpath:` resources, `tap>` for Portal inspection, clean `stop` - the whole system is just a value.

</v-click>

---

# Why this matters

<v-clicks>

- **Separation of concerns** - implementation in Clojure; config as data (EDN or YAML)
- **Ops-friendly** - the config is readable without knowing EDN (if you want)
- **Profile-aware** - `!profile` handles env differences without branching
- **Composable** - override individual groups for tests, swap entire profiles
- **The graph is free** - donut derives start/stop order from your refs
- **Aero tags are free** - uses `aero` reader tags, converts `YAML` directives to `aero` equivalents

</v-clicks>

<v-click>

```clojure
;; Test systems override specific components, not the whole map:
(nom-> (env/config "system.yaml" :test)
       system/defs
       (assoc-in [:system/defs :fdb] test-fdb-group)
       system/start)
```

</v-click>

---
zoom: 0.92
---

# Infrastructure via Testcontainers

`!profile` switches the entire group - the rest of the system is unchanged

<div class="grid grid-cols-2 gap-4">

<div>

```yaml
fdb: !profile
  default:
    container: !system/component
      system/component-kind: fdb/container
      image-name: mono/foundationdb:7.3.75
    cluster-file-path: !system/component
      system/component-kind: fdb/cluster-file-path
      container: !system/local-ref container
    db: !system/component
      system/component-kind: fdb/db
      cluster-file-path: !system/local-ref cluster-file-path
  prod:
    db: !system/component
      system/component-kind: fdb/db
      cluster-file-path: /etc/fdb/fdb.cluster
```

</div>

<div>

```yaml
example-bookmark:
  store: !system/component
    system/component-kind: example-bookmark/store
    record-db: !system/ref fdb.record-db
    record-store: !system/ref fdb.bookmark-store

server:
  handler: !system/required-component
  interceptors: !system/component
    system/component-kind: server/interceptors
    bookmark-store: !system/ref example-bookmark.store
  jetty-adapter: !system/component
    system/component-kind: server/jetty-adapter
    handler: !system/local-ref handler
    interceptors: !system/local-ref interceptors
    options:
      port: !profile
        dev: 8080
        default: 0
```

</div>
</div>

<v-click>

Containers for FDB, PostgreSQL, Pulsar, MQTT, Vault - and a generic `:testcontainers` group for anything else. All registered by requiring one interface namespace.

</v-click>

---
zoom: 0.8
---

# System component library

mono ships registered component kinds for the full stack.

<v-clicks>

**`:fdb`** - `cluster-file-path` · `db` · `record-db` · `store` · `meta-store` · `watcher` · `watchers`

**`:pulsar`** - `client` · `admin` · `producer(s)` · `consumer(s)` · `reader(s)` · `schemas` · `topics` · `namespaces` · `tenants` · `broker-url` · `http-service-url` · crypto key pair/reader variants · `message-bus-producers/consumers`

**`:mqtt`** - `client` · `producers` · `consumers` · `message-bus-producers/consumers`

**`:message-bus`** - `bus` (Pulsar-backed) · `local-bus` (core.async channels)

**`:db`** - `datasource` · `datasources` (next.jdbc)

**`:migrator`** - `migrations` (Liquibase)

**`:server`** - `interceptors` · `jetty-adapter` (reitit + malli + ring)

**`:command-processor`** - `command-processor` (bus + processor + channels)

**`:telemetry`** - `otel-sdk` (OpenTelemetry + OTLP exporter)

**`:vault`** - `client` (Hashicorp vault)

</v-clicks>

---
layout: center
---

# Thanks

`github.com/kjothen/mono`

<div class="mt-8 opacity-60 text-sm">
  donut.system &middot; aero &middot; clj-yaml &middot; Polylith
</div>
