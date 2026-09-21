<!-- markdownlint-configure-file { "MD013": false, "MD033": false, "MD041": false } -->

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/logo-dark.svg" />
    <img src="docs/assets/logo.svg" alt="mono" width="200" />
  </picture>
</p>

# mono

**The layer you build on.** An opinionated Clojure framework for building any
kind of system, on the [Polylith](https://polylith.gitbook.io/polylith)
architecture.

## What It Is

mono helps you build systems: services assembled from bricks you can test on
their own, wired together by configuration and started as one.

It makes the decisions a team would otherwise make service by service: which
libraries to use, wiring and lifecycle, per-environment config, errors across
boundaries, message shapes, and tests against real infrastructure.

The wiring is data. A system is a YAML file you can read, a map you can print
at the REPL, and a lifecycle you start and stop. Every brick behind it is plain
functions returning values or anomalies.

## Components

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/assets/icons/system-dark.svg" /><img src="docs/assets/icons/system.svg" alt="System" title="System" width="48" height="48" /></picture>
  &nbsp;&nbsp;&nbsp;
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/assets/icons/persistence-dark.svg" /><img src="docs/assets/icons/persistence.svg" alt="Persistence" title="Persistence" width="48" height="48" /></picture>
  &nbsp;&nbsp;&nbsp;
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/assets/icons/messaging-dark.svg" /><img src="docs/assets/icons/messaging.svg" alt="Messaging" title="Messaging" width="48" height="48" /></picture>
  &nbsp;&nbsp;&nbsp;
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/assets/icons/web-http-dark.svg" /><img src="docs/assets/icons/web-http.svg" alt="Web &amp; HTTP" title="Web &amp; HTTP" width="48" height="48" /></picture>
  &nbsp;&nbsp;&nbsp;
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/assets/icons/mail-dark.svg" /><img src="docs/assets/icons/mail.svg" alt="Mail" title="Mail" width="48" height="48" /></picture>
  &nbsp;&nbsp;&nbsp;
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/assets/icons/security-dark.svg" /><img src="docs/assets/icons/security.svg" alt="Security &amp; Cryptography" title="Security &amp; Cryptography" width="48" height="48" /></picture>
  &nbsp;&nbsp;&nbsp;
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/assets/icons/serialisation-dark.svg" /><img src="docs/assets/icons/serialisation.svg" alt="Serialisation" title="Serialisation" width="48" height="48" /></picture>
  &nbsp;&nbsp;&nbsp;
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/assets/icons/scheduling-dark.svg" /><img src="docs/assets/icons/scheduling.svg" alt="Scheduling" title="Scheduling" width="48" height="48" /></picture>
  &nbsp;&nbsp;&nbsp;
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/assets/icons/observability-dark.svg" /><img src="docs/assets/icons/observability.svg" alt="Observability" title="Observability" width="48" height="48" /></picture>
  &nbsp;&nbsp;&nbsp;
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/assets/icons/testing-dark.svg" /><img src="docs/assets/icons/testing.svg" alt="Testing" title="Testing" width="48" height="48" /></picture>
</p>

```text
  components: 37   facade: 2   abstraction: 4   curated: 22   own: 9

  kind: f = facade   a = abstraction   c = curated   - = no library

  brick                  kind  library                     purpose
  -----------------------------------------------------------------------------------------------------------
  system
    cli                  --c   tools.cli                   CLI argument validation and exit handling
    env                  --c   aero, clj-yaml              Configuration loading with :dev/:test/:prod profiles
    error                --c   nom                         Anomaly-based error handling, and its combinators
    log                  --c   tools.logging, logback      Structured logging
    system               --c   donut.system                Lifecycle management, systems as data
    utility              --c   uuid-creator, ulid-creator  Deep merge, UUID v7, ULID, time, collection helpers
  persistence
    cache                --c   core.cache                  In-memory caching
    jdbc                 f--   next.jdbc                   All of next.jdbc, returning anomalies
    migrator             --c   liquibase-core              Liquibase schema migrations
  messaging
    command              ---   -                           Request-reply and async command dispatch over bus
    command-processor    ---   -                           Bus-subscription lifecycle for domain processors
    command-schema       ---   -                           Command Avro schemas (envelope, response, command)
    event                ---   -                           Event publication and processing
    event-processor      ---   -                           Bus-subscription lifecycle for event handlers
    event-schema         ---   -                           Event envelope Avro schema
    kafka                --c   kafka-clients               Kafka producers/consumers, Avro-serialised values
    message-bus          -a-   -                           Protocol over messaging backends, local or Pulsar
    mqtt                 --c   machine_head                MQTT publish/subscribe
    processor            ---   -                           Message processor protocol
    pulsar               --c   pulsar-client               Apache Pulsar producer/consumer/reader with Avro
  web & http
    http-client          f--   http-kit                    All of http-kit's client, returning anomalies
    server               --c   reitit, jetty9              Jetty with interceptor-based DI and OpenAPI
  mail
    smtp                 --c   angus-mail                  Sending email over SMTP submission, text and HTML
  security & cryptography
    auth                 --c   buddy-hashers, buddy-sign   Password hashing, JWT, Authorization parsing
    encryption           --c   buddy-core                  RSA keys, opaque tokens, constant-time comparison
    identity-provider    -a-   buddy-sign                  Service-account and token protocol, with local impl
    keycloak             --c   buddy-sign                  Keycloak-backed identity-provider implementation
    pulsar-vault-crypto  --c   pulsar-client               Tenant-scoped Pulsar message encryption via Vault
    secret               -a-   -                           Secret resolution: env, pass, GCP Secret Manager
    vault                --c   vault-clj                   HashiCorp Vault for secrets and key management
  serialisation
    avro                 --c   lancaster, abracad          Apache Avro schema-based serialisation
    json                 -a-   data.json                   JSON read/write, the library underneath swappable
  scheduling
    scheduler            --c   cronut                      In-memory cron scheduling of named jobs
  observability
    telemetry            --c   clj-otel                    OpenTelemetry tracing with W3C traceparent
  testing
    test-resources       ---   -                           Shared test configuration
    test-system          ---   -                           with-test-system lifecycle, nom-test> assertions
    testcontainers       --c   testcontainers              Declarative container infrastructure for tests
```

The kind says how a brick relates to the library it is built on:

- **Facade** — the library's whole API, wrapped so every call returns an
  anomaly instead of throwing. You should not need to require the library
  yourself.
- **Abstraction** — the interface names operations rather than a library, so
  what is underneath can be swapped without touching a single call site.
- **Curated** — mono's own API, using the library inside. It covers what mono
  needs; for anything beyond it, require the library directly and declare it in
  your `deps.edn`.
- **No library** — the brick is mono's own code.

## How to Use It

### Start a new workspace (recommended)

Generate a Polylith workspace already wired to mono as a library:

```bash
clojure -Ttools install-latest :lib io.github.seancorfield/deps-new :as new

clojure -Tnew create \
  :template 'io.github.repldriven/mono%template%com.repldriven.mono/template#v0.0.36' \
  :name com.acme/my-thing
```

You get a workspace whose shared bricks come from mono as a pinned git
dependency, plus the example bricks copied in and rewritten into your own
namespace for you to own and edit. See `template/` for how it works.

### The RealWorld example

`realworld-domain`, `realworld-store` and `realworld-api` are an example rather
than library code, and are not in `mono-lib`: a
[RealWorld](https://realworld-docs.netlify.app/) service over postgres, held to
the official conformance suite by `just realworld-hurl`. The template copies
them into a new workspace for you to edit or delete.

### Use mono as a library from an existing workspace

Shared components are published as a git dependency. No Maven or Clojars is
involved; everything resolves from a tag and its sha.

```clojure
{:deps {com.repldriven/mono
        {:git/url "https://github.com/repldriven/mono.git"
         :git/tag "v0.0.36"
         :git/sha "<full-sha>"
         :deps/root "projects/mono-lib"}}

 :aliases
 {:test {:extra-deps
         {com.repldriven/mono
          {:git/url "https://github.com/repldriven/mono.git"
           :git/tag "v0.0.36"
           :git/sha "<full-sha>"
           :deps/root "projects/mono-test-lib"}}}}}
```

`mono-test-lib` is `mono-lib` plus test support, so keep it under `:test`.
Use the same lib symbol for both. Bump the tag and sha together.

## Who Uses It

- [Queenswood](https://github.com/repldriven/queenswood) — core banking, boxed:
  accounts, payments, a double-entry ledger, interest and onboarding. It
  consumes mono as a library, so it is also the reference for depending on it.

## Documentation

- `docs/adr/` — architecture decisions, one per load-bearing choice.
- `docs/prd/` — product requirements, one per capability a workspace
  built on these bricks gets.
- `docs/tdd/` — technical designs, the engineering contract behind
  each PRD.
- `docs/recipes/code/` — writing to these bricks: components, bases,
  projects, code style, common helpers, error handling, system
  components and system configurations.
- `docs/recipes/test/` — test systems, and the containers they run
  against.
- `docs/recipes/practices/` — working on the repository itself: git
  flow against Renovate, how a `just` recipe is written, and how these
  documents are written.
- `plugins/` — the Tessl rules distilled from both, loaded through
  `AGENTS.md`; see [plugins/README.md](plugins/README.md).

Every recipe keeps the same shape — `Problem`, `Solution`, `Rules`,
`Discussion`, `References` — and carries a `<!-- tessl-plugin: <name> -->`
label naming the rule that distils its `## Rules`. A workspace built on
these bricks lays the ADRs and recipes down beside its own at the sha
it pins, and installs the plugins beside its own.

## Presentations

1. [Systems as data](./docs/slides/systems-as-data/slides.md) — how mono
   uses donut.system to build a system of components from configuration
   data

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

That checks you have nix and direnv. There is nothing native to install and no
code generation step: a JDK, Clojure and Docker are the whole toolchain, and
the devshell provides them.

### Run the tests

```bash
just test
```

## Key Patterns

- **Systems are values** — a started system is a map, with no global state.
- **Anomalies, not exceptions** — a component returns an anomaly instead of
  throwing, even where the library underneath throws, so failures thread through
  `nom->` and `let-nom>` like any other value.
- **Keyword keys throughout** — kebab-case, whether the data came from HTTP,
  Pulsar or MQTT.
- **Injection through interceptors** — handlers get their dependencies from
  the request context, never from dynamic vars or atoms.
- **Containers as components** — a test declares the infrastructure it needs,
  such as a database, a broker or a vault, as containers in YAML, started by
  the same lifecycle as production.

## Built On

The libraries mono's components wrap, the bricks that wrap them, and the tools
the workspace is built with.

### Clojure

- [abracad](https://github.com/nomnom-insights/abracad) — avro
- [aero](https://github.com/juxt/aero) — env
- [buddy-core](https://github.com/funcool/buddy-core) — encryption
- [buddy-hashers](https://github.com/funcool/buddy-hashers) — auth
- [buddy-sign](https://github.com/funcool/buddy-sign) — auth, identity-provider, keycloak
- [clj-otel](https://github.com/steffan-westcott/clj-otel) — telemetry
- [clj-yaml](https://github.com/clj-commons/clj-yaml) — env
- [core.cache](https://github.com/clojure/core.cache) — cache
- [cronut](https://github.com/factorhouse/cronut) — scheduler
- [data.json](https://github.com/clojure/data.json) — json
- [donut.system](https://github.com/donut-party/system) — system
- [http-kit](https://github.com/http-kit/http-kit) — http-client
- [lancaster](https://github.com/deercreeklabs/lancaster) — avro
- [machine_head](https://github.com/clojurewerkz/machine_head) — mqtt
- [next.jdbc](https://github.com/seancorfield/next-jdbc) — jdbc
- [nom](https://github.com/otto-de/nom) — error
- [reitit](https://github.com/metosin/reitit) — server
- [ring-jetty9-adapter](https://github.com/sunng87/ring-jetty9-adapter) — server
- [tools.cli](https://github.com/clojure/tools.cli) — cli
- [tools.logging](https://github.com/clojure/tools.logging) — log
- [vault-clj](https://github.com/amperity/vault-clj) — vault

### Java

- [angus-mail](https://github.com/eclipse-ee4j/angus-mail) — smtp
- [kafka-clients](https://github.com/apache/kafka) — kafka
- [liquibase](https://github.com/liquibase/liquibase) — migrator
- [logback](https://github.com/qos-ch/logback) — log
- [pulsar-client](https://github.com/apache/pulsar) — pulsar, pulsar-vault-crypto
- [testcontainers](https://github.com/testcontainers/testcontainers-java) — testcontainers
- [ulid-creator](https://github.com/f4b6a3/ulid-creator) — utility
- [uuid-creator](https://github.com/f4b6a3/uuid-creator) — utility

### Tools

- [clj-kondo](https://github.com/clj-kondo/clj-kondo) — linting, in the
  pre-commit hook
- [just](https://github.com/casey/just) — the task runner
- [Nix](https://github.com/NixOS/nix) — the development shell: JDK, Clojure,
  just and the lint and format tools
- [Polylith](https://github.com/polyfy/polylith) — workspace, dependency checks
  and incremental testing
- [Renovate](https://github.com/renovatebot/renovate) — dependency updates
- [semgrep](https://github.com/semgrep/semgrep) — project rules, in the
  pre-commit hook
- [zprint](https://github.com/kkinnear/zprint) — formatting, in the pre-commit
  hook
