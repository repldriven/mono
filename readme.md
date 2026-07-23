# mono

A Clojure monorepo for building production-ready distributed systems,
following the [Polylith](https://polylith.gitbook.io/polylith) software architecture.

> **Looking for Queenswood Bank?** The banking domain that was built on
> mono is now [kjothen/queenswood](https://github.com/kjothen/queenswood)
> — a full fork and a good example of what you can build with this repo.

## What It Is

`mono` is a component library and reference implementation for composing
production systems from well-defined, independently testable building blocks.
Systems are described as data — YAML/EDN configuration files drive lifecycle,
dependency injection, and environment management, with no global state and no
framework magic.

## How to Use It

### Start a new workspace (recommended)

Generate a Polylith workspace already wired to mono as a library:

```bash
clojure -Ttools install-latest :lib io.github.seancorfield/deps-new :as new

clojure -Tnew create \
  :template 'io.github.repldriven/mono%template%com.repldriven.mono/template#v0.0.9' \
  :name com.acme/my-thing
```

You get a workspace whose shared bricks come from mono as a pinned git
dependency, plus the example bricks copied in and rewritten into your own
namespace for you to own and edit. See `template/` for how it works.

If your project name uses a git-service qualifier, say what top namespace you
want, because deps-new strips those prefixes:

```bash
clojure -Tnew create ... :name io.github.acme/my-thing :top-ns '"com.acme.my-thing"'
```

### Use mono as a library from an existing workspace

Shared components are published as a git dependency. No Maven or Clojars is
involved; everything resolves from a tag and its sha.

```clojure
{:deps {com.repldriven/mono
        {:git/url "https://github.com/repldriven/mono.git"
         :git/tag "v0.0.9"
         :git/sha "<full-sha>"
         :deps/root "projects/mono-lib"}}

 :aliases
 {:test {:extra-deps
         {com.repldriven/mono
          {:git/url "https://github.com/repldriven/mono.git"
           :git/tag "v0.0.9"
           :git/sha "<full-sha>"
           :deps/root "projects/mono-test-lib"}}}}}
```

`projects/mono-lib` carries the reusable components. `projects/mono-test-lib`
adds the test support (`test-system`, `testcontainers`) and belongs under a
`:test` alias only, so Docker and the testcontainers tree stay off your runtime
classpath.

Both ship under **one lib symbol**, `com.repldriven/mono`, differing only by
`:deps/root`. tools.deps checks out a git dep once per lib symbol, so two
symbols would mean two checkouts, and every component the two roots share would
appear at two absolute paths under the same component lib symbol — which
tools.deps refuses to reconcile ("No known ancestor relationship between local
versions"). One symbol means `:extra-deps` replaces the runtime root rather
than adding to it, which is why the test root is a superset of the runtime one.

Require bricks by their original namespaces, for example
`com.repldriven.mono.error.interface`. The namespace belongs to the brick's
source, not to how it was delivered.

To take an upstream fix, bump the tag and sha together. Published tags never
move; mono cuts a new one instead.

## Presentations

1. [Systems as data - How mono uses donut.system to build a system of components from configuration data](./slides/systems_as_data/slides.md)

## Getting Started

### Prerequisites

- [Nix](https://nixos.org/) — all dependencies are managed through the Nix
  development shell
- [direnv](https://direnv.net/) — automatically loads the Nix environment when
  you `cd` into the repo. Install globally with:

  ```bash
  nix profile install nixpkgs#direnv
  ```

- Docker (for integration tests via Testcontainers). On Mac OS X, run
  `just start-docker` to start Colima.

Verify your setup with:

```bash
./scripts/check-setup.sh
```

### Run all tests

```bash
clojure -M:poly test project:dev
```

### Test a specific component

```bash
clojure -M:poly test brick:command project:dev
```

## Components

Each brick lists the library it is built on, and **how it relates to it**:

| Kind            | What it means                                                                                                                                             |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Facade**      | The library's whole API, wrapped so every call returns an anomaly instead of throwing. You should not need to require the library yourself.                |
| **Abstraction** | The interface names operations rather than a library, so what is underneath can be swapped without touching a single call site.                            |
| **Curated**     | Mono's own API, using the library inside. It covers what mono needs; for anything beyond it, require the library directly and declare it in your `deps.edn`. |
| **—**           | No third-party library — the brick is mono's own code.                                                                                                     |

The distinction is not stylistic. A library gets a **facade** when its API is
wide and it signals failure by throwing: next.jdbc is 25 functions and macros,
http-kit's documented idiom is `@(http/get url)`, and wrapping only the popular
calls reads like the library right up to the moment it doesn't. A library gets
an **abstraction** only when the operations are genuinely generic — parsing JSON
is, connection pooling is not.

Everything else is **curated**, and two libraries got no brick at all: malli and
honeysql, whose real interfaces are *data* — a schema is a vector you hand to
reitit, a query is a map you hand to a formatter — so wrapping their functions
bought nothing but a namespace hop. Require those directly.

Whatever the kind, the library stays on your classpath, so reaching past a brick
is always available. Declare it in your own `deps.edn` when you do, rather than
relying on ours.

### System

| Component | Purpose                                                    | Library                     | Kind    |
| --------- | ---------------------------------------------------------- | --------------------------- | ------- |
| `cli`     | CLI argument validation and exit handling                  | `tools.cli`                 | Curated |
| `env`     | Configuration loading with `:dev`/`:test`/`:prod` profiles | `aero`, `clj-yaml`          | Curated |
| `error`   | Anomaly-based error handling, and the combinators for it   | `nom`                       | Curated |
| `log`     | Structured logging                                         | `tools.logging`, `logback`  | Curated |
| `system`  | Lifecycle management, systems as data                      | `donut.system`              | Curated |
| `utility` | Deep merge, UUID v7, ULID, time, collection helpers        | `uuid-creator`, `ulid-creator` | Curated |

### Persistence

| Component  | Purpose                                                        | Library                    | Kind    |
| ---------- | -------------------------------------------------------------- | -------------------------- | ------- |
| `cache`    | In-memory caching                                              | `core.cache`               | Curated |
| `fdb`      | FoundationDB — KV layer, record layer, changelog processing    | `fdb-java`, `fdb-record-layer-core` | Curated |
| `jdbc`     | All of next.jdbc, returning anomalies; kebab keys, snake_case SQL | `next.jdbc`             | Facade  |
| `migrator` | Liquibase schema migrations                                    | `liquibase-core`           | Curated |

### Messaging

| Component           | Purpose                                            | Library         | Kind        |
| ------------------- | -------------------------------------------------- | --------------- | ----------- |
| `command`           | Request-reply and async command dispatch over bus  | —               | —           |
| `command-processor` | Bus-subscription lifecycle for domain processors   | —               | —           |
| `command-schema`    | Command Avro schemas (envelope, response, command) | —               | —           |
| `event`             | Event publication and processing                   | —               | —           |
| `event-processor`   | Bus-subscription lifecycle for event handlers      | —               | —           |
| `event-schema`      | Event envelope Avro schema                         | —               | —           |
| `message-bus`       | Protocol over messaging backends — local or Pulsar | —               | Abstraction |
| `mqtt`              | MQTT publish/subscribe                             | `machine_head`  | Curated     |
| `processor`         | Message processor protocol                         | —               | —           |
| `pulsar`            | Apache Pulsar producer/consumer/reader with Avro   | `pulsar-client` | Curated     |

### Web & HTTP

| Component     | Purpose                                                         | Library            | Kind    |
| ------------- | ----------------------------------------------------------------- | ------------------ | ------- |
| `http-client` | All of http-kit's client, returning anomalies; `-async` twins    | `http-kit`         | Facade  |
| `server`      | Jetty with interceptor-based dependency injection and OpenAPI    | `reitit`, `jetty9` | Curated |

### Security & Cryptography

| Component             | Purpose                                             | Library        | Kind        |
| --------------------- | --------------------------------------------------- | -------------- | ----------- |
| `encryption`          | RSA keys, opaque tokens, constant-time comparison   | `buddy-core`   | Curated     |
| `identity-provider`   | Service-account and token protocol, with local impl | `buddy-sign`   | Abstraction |
| `keycloak`            | Keycloak-backed `identity-provider` implementation  | `buddy-sign`   | Curated     |
| `pulsar-vault-crypto` | Tenant-scoped Pulsar message encryption via Vault   | `pulsar-client`| Curated     |
| `secret`              | Secret resolution — env, `pass`, GCP Secret Manager | —              | Abstraction |
| `vault`               | HashiCorp Vault for secrets and key management      | `vault-clj`    | Curated     |

### Serialisation

| Component | Purpose                                                     | Library     | Kind        |
| --------- | ------------------------------------------------------------- | ----------- | ----------- |
| `avro`    | Apache Avro schema-based serialisation                       | `lancaster`, `abracad` | Curated |
| `json`    | JSON read/write — the library underneath is swappable        | `data.json` | Abstraction |

### Scheduling

| Component   | Purpose                                        | Library  | Kind    |
| ----------- | ---------------------------------------------- | -------- | ------- |
| `scheduler` | In-memory cron scheduling of named jobs        | `cronut` | Curated |

### Observability

| Component   | Purpose                                                | Library    | Kind    |
| ----------- | ------------------------------------------------------ | ---------- | ------- |
| `telemetry` | OpenTelemetry tracing with W3C traceparent propagation | `clj-otel` | Curated |

### Testing

| Component        | Purpose                                                    | Library          | Kind    |
| ---------------- | ---------------------------------------------------------- | ---------------- | ------- |
| `test-resources` | Shared test configuration                                  | —                | —       |
| `test-schema`    | Protobuf and Avro test fixtures                            | `protojure`      | Curated |
| `test-system`    | `with-test-system` lifecycle macro, `nom-test>` assertions | —                | —       |
| `testcontainers` | Declarative container infrastructure for integration tests | `testcontainers` | Curated |

`example-bookmark` is not shared: it is the starter domain the template copies
into a new workspace, and yours to edit or delete.

## Mono Bases

| Base                   | Purpose                                            |
| ---------------------- | -------------------------------------------------- |
| `build`                | Uberjar build tooling and Protobuf code generation |
| `external-test-runner` | Out-of-process test runner for Polylith            |
| `service`              | Generic async command handler entry point          |

## Key Patterns

**No global state** — systems are values; started systems are maps.

**Testcontainers as system components** — FoundationDB, Pulsar, Vault, and other
infrastructure are declared in test YAML configs and managed by the same
lifecycle machinery used in production.

**Interceptor injection** — HTTP handlers receive datasources, message clients,
and other dependencies through request context, not through dynamic vars or
atoms.

**Keyword keys throughout** — all data, including from Pulsar, MQTT, and HTTP
request bodies, uses kebab-case keyword keys.

**Anomalies, not exceptions** — a component interface returns an anomaly rather
than throwing, so failures thread through `nom->` and `let-nom>` alongside
values. That holds even where the library underneath throws: malformed key
bytes, an unparseable cron expression and a rejected Vault login are all
ordinary runtime conditions, not bugs. The exceptions are deliberate and
documented where they occur — telemetry degrades rather than failing, and a
brick implementing a Java interface obeys that interface's contract.

## Tooling

- **[Polylith](https://polylith.gitbook.io/)** — workspace management and
  incremental testing
- **[donut.system](https://github.com/donut-party/system)** — component
  lifecycle and dependency injection
- **[zprint](https://github.com/kkinnear/zprint)** — code formatting (80-char
  width, enforced by pre-commit hook)
- **[clj-kondo](https://github.com/clj-kondo/clj-kondo)** — linting (enforced
  by pre-commit hook)
- **[Renovate](https://docs.renovatebot.com/)** — automated dependency updates
