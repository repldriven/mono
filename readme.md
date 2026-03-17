# mono

A Clojure monorepo for building production-ready distributed systems, organised
as a [Polylith](https://polylith.gitbook.io/polylith) workspace.

## What It Is

`mono` is a component library and reference implementation for composing
production systems from well-defined, independently testable building blocks.
Systems are described as data — YAML/EDN configuration files drive lifecycle,
dependency injection, and environment management, with no global state and no
framework magic.

## Exemplar: Queenswood Bank

The repo ships an end-to-end banking application — **Queenswood** — that
onboards customers and manages accounts. It demonstrates how the component
library composes into a production-shaped system.

### Customer Onboarding Flow

1. **Create an organisation** — an admin creates a tenant and receives an API
   key (prefixed `sk_live_`, returned once, stored hashed).
2. **Create a party** — the organisation uses its API key to register a
   customer with personal details and a national identifier (uniqueness
   enforced).
3. **Identity verification** — an IDV record is automatically created and
   accepted, triggering the party's transition from `pending` to `active`.
4. **Open an account** — only active parties may open accounts. Each account
   is assigned a UK SCAN payment address (sort code + sequential account
   number).
5. **Account lifecycle** — accounts move through `opened` → `closing` →
   `closed`, driven by API calls and reactive watchers.

[![Account Opening Demo](thumbnail.png)](https://github.com/user-attachments/assets/671b9d87-6cb5-482d-a53c-578d086734e9)

### How It Works

Queenswood is assembled from the component library:

- **FoundationDB Record Layer** stores organisations, parties, accounts, and
  IDV records with multi-store transactions for atomicity.
- **Changelog watchers** on FDB drive the reactive flow — IDV acceptance
  activates the party; account closing auto-transitions to closed.
- **Apache Pulsar** carries commands between the HTTP API and processors,
  with Avro-serialised messages and request-reply.
- **Reitit + Malli** provide routing, schema validation, and OpenAPI spec
  generation.
- The whole system — containers, message brokers, databases — is declared in
  YAML and started by the same lifecycle machinery used in tests.

### Running It

Start a REPL with `just repl` and connect your editor. The development
entry point follows the standard Polylith pattern — a namespace under
`development/src/dev/` that requires the base and Testcontainers:

```clojure
;; development/src/dev/bank_monolith.clj — evaluate the comment block
(def sys
  (main/start "classpath:bank-monolith/application-test.yml"
              :dev))
(main/stop sys)
```

This boots the full system — FDB, Pulsar, HTTP server — inside
Testcontainers. Then start the Svelte front-end:

```bash
just start-bank-app
```

### API Surface

| Endpoint                               | Description                         |
| -------------------------------------- | ----------------------------------- |
| `POST /v1/organizations`               | Create organisation + API key       |
| `POST /v1/parties`                     | Register a customer                 |
| `GET /v1/parties[/{party-id}]`         | List / retrieve parties             |
| `POST /v1/accounts`                    | Open an account for an active party |
| `GET /v1/accounts[/{account-id}]`      | List / retrieve accounts            |
| `POST /v1/accounts/{account-id}/close` | Initiate account closure            |

Interactive OpenAPI documentation is served at
[http://localhost:8080](http://localhost:8080).

### Command Request/Reply Flow

Commands travel from the HTTP API through Pulsar to domain processors and back.
Each flow follows the same pattern:

```
bank-api ─► Pulsar command topic ─► command-processor ─► domain processor
                                                            ↓
bank-api ◄─ Pulsar response topic ◄─ command response ◄─────┘
```

**Create a party** — `POST /v1/parties`

```
bank-api                    command-processor         PartyProcessor
   │                              │                        │
   ├── serialize body ──────────► │                        │
   │   (parties-command topic)    ├── dispatch ──────────► │
   │                              │   "create-party"       ├── create party (pending)
   │                              │                        ├── create person-identification
   │                              │                        ├── create national-identifier
   │                              │                        │   (FDB transaction)
   │  (parties-command-response)  │ ◄── ACCEPTED ──────────┤
   ◄──────────────────────────────┤                        │
   │                              │
   │  ┌─── IDV watcher (async) ──────────────────────────────┐
   │  │ FDB changelog fires when IDV status → accepted       │
   │  │ Party transitions from pending → active              │
   │  └──────────────────────────────────────────────────────┘
```

**Open an account** — `POST /v1/accounts` (requires active party)

```
bank-api                    command-processor         AccountProcessor
   │                              │                        │
   ├── serialize body ──────────► │                        │
   │   (accounts-command topic)   ├── dispatch ──────────► │
   │                              │   "open-account"       ├── verify party is active
   │                              │                        ├── create account (opened)
   │                              │                        ├── assign SCAN address
   │                              │                        │   (FDB transaction)
   │  (accounts-command-response) │ ◄── ACCEPTED ──────────┤
   ◄──────────────────────────────┤                        │
```

**Close an account** — `POST /v1/accounts/{account-id}/close`

```
bank-api                    command-processor         AccountProcessor
   │                              │                        │
   ├── serialize body ──────────► │                        │
   │   (accounts-command topic)   ├── dispatch ──────────► │
   │                              │   "close-account"      ├── load account
   │                              │                        ├── set status → closing
   │                              │                        │   (FDB transaction)
   │  (accounts-command-response) │ ◄── ACCEPTED ──────────┤
   ◄──────────────────────────────┤                        │
   │                              │
   │  ┌─── Account watcher (async) ──────────────────────────┐
   │  │ FDB changelog fires when status → closing            │
   │  │ Account transitions from closing → closed            │
   │  └──────────────────────────────────────────────────────┘
```

All commands are Avro-serialised. Responses use envelope statuses: `ACCEPTED`
(2xx), `REJECTED` (4xx), or `FAILED` (5xx).

## Architecture

### Polylith

Three artifact types live in this repo:

| Type           | Location      | Role                                                          |
| -------------- | ------------- | ------------------------------------------------------------- |
| **Components** | `components/` | Reusable building blocks with a stable public interface       |
| **Bases**      | `bases/`      | Application entry points (`-main`, HTTP handlers, processors) |
| **Projects**   | `projects/`   | Deployable applications — just `deps.edn`, no code            |

Components expose a single `interface.clj`. Nothing in this repo reaches
into another component's internals.

### System-as-Data

A running application is the product of a configuration file:

```
config (YAML/EDN) → system definitions → started system
```

Components register themselves via `system/defcomponents`. Projects wire
components together by listing them in `deps.edn`. Infrastructure — databases,
message queues, Vault — is just another system component.

### Error Handling

No exceptions cross component boundaries. All failure paths return anomalies:

```clojure
;; Short-circuits on first failure
(error/let-nom> [conn   (db/connect datasource)
                 result (sql/execute conn query)]
  result)
```

Macros — `try-nom`, `let-nom>`, `nom->`, `nom-do>` — compose anomaly-aware
pipelines without defensive `try/catch` noise.

## Components

### Foundation

| Component | Purpose                                                    |
| --------- | ---------------------------------------------------------- |
| `system`  | Lifecycle management wrapping `donut.system`               |
| `error`   | Anomaly-based error handling (`nom` library)               |
| `env`     | Configuration loading with `:dev`/`:test`/`:prod` profiles |
| `log`     | Structured logging                                         |
| `utility` | Deep merge, UUID v7, YAML conversion, collection helpers   |
| `spec`    | Malli-based validation with human-readable errors          |
| `cli`     | CLI argument validation and exit handling                  |

### Persistence

| Component  | Purpose                                                     |
| ---------- | ----------------------------------------------------------- |
| `db`       | PostgreSQL with connection pooling                          |
| `sql`      | HoneySQL query formatting                                   |
| `migrator` | Liquibase schema migrations                                 |
| `fdb`      | FoundationDB — KV layer, record layer, changelog processing |

### Messaging

| Component           | Purpose                                           |
| ------------------- | ------------------------------------------------- |
| `pulsar`            | Apache Pulsar producer/consumer/reader with Avro  |
| `mqtt`              | MQTT publish/subscribe                            |
| `message-bus`       | Protocol abstraction over messaging backends      |
| `command`           | Request-reply and async command dispatch over bus |
| `processor`         | Message processor protocol                        |
| `command-processor` | Bus-subscription lifecycle for domain processors  |

### Web & HTTP

| Component     | Purpose                                                       |
| ------------- | ------------------------------------------------------------- |
| `server`      | Jetty with interceptor-based dependency injection and OpenAPI |
| `http-client` | HTTP client with anomaly-based error handling                 |

### Security & Cryptography

| Component             | Purpose                                           |
| --------------------- | ------------------------------------------------- |
| `vault`               | HashiCorp Vault for secrets and key management    |
| `encryption`          | AES-256, RSA, base64                              |
| `pulsar-vault-crypto` | Tenant-scoped Pulsar message encryption via Vault |

### Serialisation

| Component | Purpose                                                      |
| --------- | ------------------------------------------------------------ |
| `avro`    | Apache Avro schema-based serialisation                       |
| `schemas` | Protobuf definitions (Person, Account, Organization, ApiKey) |
| `json`    | JSON read/write with anomaly errors                          |

### Observability

| Component   | Purpose                                                |
| ----------- | ------------------------------------------------------ |
| `telemetry` | OpenTelemetry tracing with W3C traceparent propagation |

### Domain

| Component       | Purpose                                                                   |
| --------------- | ------------------------------------------------------------------------- |
| `accounts`      | Account lifecycle — open, close, suspend, reopen, archive                 |
| `organizations` | Organisation management — create org, API key generation and verification |
| `party`         | Party creation and management                                             |
| `idv`           | Identity verification processing                                          |

### Testing

| Component        | Purpose                                                    |
| ---------------- | ---------------------------------------------------------- |
| `test-system`    | `with-test-system` lifecycle macro, `nom-test>` assertions |
| `testcontainers` | Declarative container infrastructure for integration tests |
| `test-resources` | Shared test configuration                                  |
| `test-schemas`   | Protobuf test fixtures                                     |

## Deployed Applications

| Project            | Base       | Description                                     |
| ------------------ | ---------- | ----------------------------------------------- |
| `bank-web`         | `bank-api` | Account lifecycle API (open, close, suspend, …) |
| `accounts-service` | `service`  | Async command handler for account operations    |

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

### REPL

```bash
clojure -M:dev:nrepl
# or with Rebel Readline
clojure -M:dev:rebel
```

### Code formatting

```bash
clojure -M:format/zprint --formatted path/to/file.clj
```

### Linting

```bash
clojure -M:lint/clj-kondo --lint bases components
```

## Key Patterns

**Keyword keys throughout** — all data, including from Pulsar, MQTT, and HTTP
request bodies, uses kebab-case keyword keys.

**No global state** — systems are values; started systems are maps.

**Interceptor injection** — HTTP handlers receive datasources, message clients,
and other dependencies through request context, not through dynamic vars or
atoms.

**Testcontainers as system components** — FoundationDB, Pulsar, Vault, and other
infrastructure are declared in test YAML configs and managed by the same
lifecycle machinery used in production.

## Tooling

- **[Polylith](https://polylith.gitbook.io/)** — workspace management and
  incremental testing
- **[zprint](https://github.com/kkinnear/zprint)** — code formatting (80-char
  width, enforced by pre-commit hook)
- **[clj-kondo](https://github.com/clj-kondo/clj-kondo)** — linting (enforced
  by pre-commit hook)
- **[Renovate](https://docs.renovatebot.com/)** — automated dependency updates
