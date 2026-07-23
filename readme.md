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
  :template 'io.github.repldriven/mono%template%com.repldriven.mono/template#v0.0.6' \
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
         :git/tag "v0.0.6"
         :git/sha "<full-sha>"
         :deps/root "projects/mono-lib"}}

 :aliases
 {:test {:extra-deps
         {com.repldriven/mono
          {:git/url "https://github.com/repldriven/mono.git"
           :git/tag "v0.0.6"
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

### Fork the repo (deprecated)

```bash
just fork <your-domain>
```

This removes the example domain and rewires configs so you can:

1. Add domain components under `components/<your-domain>-*/`
2. Add domain bases under `bases/<your-domain>-*/`
3. Add domain projects under `projects/<your-domain>-*/`
4. Register your new bricks in the `:+<your-domain>` alias in `deps.edn`

Deprecated in favour of the template above: forking gives you a full copy of
every shared brick, so upstream fixes only arrive by hand.

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

### System

| Component | Purpose                                                    |
| --------- | ---------------------------------------------------------- |
| `cli`     | CLI argument validation and exit handling                  |
| `env`     | Configuration loading with `:dev`/`:test`/`:prod` profiles |
| `error`   | Anomaly-based error handling (`nom` library)               |
| `log`     | Structured logging                                         |
| `spec`    | Malli-based validation with human-readable errors          |
| `system`  | Lifecycle management wrapping `donut.system`               |
| `utility` | Deep merge, UUID v7, YAML conversion, collection helpers   |

### Persistence

| Component  | Purpose                                                     |
| ---------- | ----------------------------------------------------------- |
| `cache`    | In-memory caching                                           |
| `db`       | PostgreSQL with connection pooling                          |
| `fdb`      | FoundationDB — KV layer, record layer, changelog processing |
| `migrator` | Liquibase schema migrations                                 |
| `sql`      | HoneySQL query formatting                                   |

### Messaging

| Component           | Purpose                                            |
| ------------------- | -------------------------------------------------- |
| `command`           | Request-reply and async command dispatch over bus  |
| `command-processor` | Bus-subscription lifecycle for domain processors   |
| `command-schema`    | Command Avro schemas (envelope, response, command) |
| `message-bus`       | Protocol abstraction over messaging backends       |
| `mqtt`              | MQTT publish/subscribe                             |
| `processor`         | Message processor protocol                         |
| `pulsar`            | Apache Pulsar producer/consumer/reader with Avro   |

### Web & HTTP

| Component     | Purpose                                                       |
| ------------- | ------------------------------------------------------------- |
| `http-client` | HTTP client with anomaly-based error handling                 |
| `server`      | Jetty with interceptor-based dependency injection and OpenAPI |

### Security & Cryptography

| Component             | Purpose                                           |
| --------------------- | ------------------------------------------------- |
| `encryption`          | AES-256, RSA, base64                              |
| `pulsar-vault-crypto` | Tenant-scoped Pulsar message encryption via Vault |
| `vault`               | HashiCorp Vault for secrets and key management    |

### Serialisation

| Component | Purpose                                |
| --------- | -------------------------------------- |
| `avro`    | Apache Avro schema-based serialisation |
| `json`    | JSON read/write with anomaly errors    |

### Observability

| Component   | Purpose                                                |
| ----------- | ------------------------------------------------------ |
| `telemetry` | OpenTelemetry tracing with W3C traceparent propagation |

### Testing

| Component        | Purpose                                                    |
| ---------------- | ---------------------------------------------------------- |
| `test-resources` | Shared test configuration                                  |
| `test-schema`    | Protobuf test fixtures and pet command processor           |
| `test-system`    | `with-test-system` lifecycle macro, `nom-test>` assertions |
| `testcontainers` | Declarative container infrastructure for integration tests |

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
