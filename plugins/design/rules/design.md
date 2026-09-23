# mono system design

How a system is built from these bricks — messaging, its payloads, system
wiring, the containers tests run against, and code generation. The choices a
workspace inherits by depending on mono, as distinct from Polylith mechanics
(`framework`) and Clojure conventions (`idioms`).

## The message bus stays behind an abstraction

Keep the message bus behind the `message-bus` brick's `Producer` /
`Consumer` protocols and its `send` / `subscribe` / `unsubscribe`
operations — never a backend directly. Each broker is its own brick
(`kafka`, `pulsar`, `mqtt`) extending the protocols in its own
`message-bus` namespace, and the Clojure-channels `local` backend inside
`message-bus` serves tests and small-footprint deployments. A component
requires neither a broker brick nor `local`: the system definition binds
each producer and consumer to a backend at startup, and a base
bare-requires the backend bricks it may bind so their component kinds
register.
See [ADR-0003](../../../docs/adr/0003-message-bus-abstraction.md).

## Messaging payloads are Avro

Command and event payloads on the message bus are Avro, via Lancaster.
Schemas live in schema bricks of their own — `command-schema` and
`event-schema` — as resources, never beside the producer or consumer
that binds to them. Producers and consumers bind to a schema at
registration, so a mismatch is caught at startup, not in production.
See [ADR-0004](../../../docs/adr/0004-avro-for-message-payloads.md).

## System components are declared in YAML, registered in Clojure

Run component lifecycle through `donut.system`, with every system
defined in a YAML (or EDN) file the `system` and `env` bricks parse
before handing it to donut. Two layers: a component kind is registered
with `system/defcomponents` — its `:system/start` / `:system/stop` fns,
configuration schema and instance schema — from the owning brick's
`system.clj`, or from `system/core.clj` aggregating a `system/` folder
once a brick has two or more definition namespaces and not before,
never from `interface.clj`, which bare-requires that namespace in the
bracketed form so multimethods extend on load; the system file, kept
beside its brick or base and loaded by classpath URL, declares under a
top-level `system:` key which components exist — each a
`!system/component` whose `system/component-kind` names a registered
kind — with what configuration, wired by the tag literals
`!system/ref`, `!system/local-ref`, `!system/required-component`,
`!profile`, `!env`, `!include` and `!strs`. A bare string is never
promoted to a ref, and a kind no `defcomponents` registered is refused
by `system/defs` as `:system/unknown-component-kind` before anything
starts, so a test or a main that names a brick's kinds loads that
brick's `interface.clj` first. `aero` resolves `!profile` at load time,
so a per-profile value or component group needs no source branch;
`!include` splits a large configuration into per-group files; `!strs`
forces string keys where a config map's keys must be strings; and a
tagged scalar another tag coerces goes in a one-item sequence —
`!keyword [!or [!env SMTP_SECURITY, starttls]]` — since YAML allows no
tag on a tagged scalar. A required component (typically the HTTP
`handler`) is a slot, `system/required-component` in the definition
and `!system/required-component` in the file, that the bootstrap
caller fills with `assoc-in` before starting. A start fn returns
`(or instance ...)`, so a prior instance survives a hot-reload. Don't
bake an environment name into a shared resource component or its
config — discriminate environments through env vars and deployment
values, `*-test-resources` naming the runtime mode rather than an
environment. Tests consolidate system-component bare requires for a
base or project into one `test/.../system.clj` namespace rather than
repeating them per file, and may alias a component beside its bare
require where the test calls it.
See [ADR-0007](../../../docs/adr/0007-system-as-data.md),
[system-components](../../../docs/recipes/code/system-components.md),
[system-configurations](../../../docs/recipes/code/system-configurations.md).

## Testcontainer infrastructure follows the three-layer pattern

Testcontainer-backed infrastructure is declared in the system file
behind a profile, so tests boot through the production code path with
a different profile and group, in three layers wired by
`!system/local-ref`: the container itself, an extractor that reads
runtime values (host, port, bootstrap servers) from the started
container — living in the relevant brick's `system/` folder, never in
`testcontainers` itself beyond its generic mapped-port and URI
extractors — and the high-level component, which consumes extracted
values exactly as it would a production literal and never branches on
whether it's running against a container. The `testcontainers` brick
may call builder-pattern setup methods during construction, never
library methods against a started container.
See [testcontainers](../../../docs/recipes/test/testcontainers.md).

## Code generation follows the prep-lib pattern

Generate code from a source artefact with Clojure's standard
`:deps/prep-lib`, never a build-system plugin or a run-script. The
brick's `deps.edn` declares `:deps/prep-lib` with an `:fn` entry point
and an `:ensure` path marking prep as up-to-date; a co-located
`build.clj` implements the generation; output lands in a brick-local
`gen/` folder, gitignored there and never committed. Regenerate with
`clj -X:deps prep :aliases '[:dev]'`, adding `:force true` after a
source change — the `:ensure` marker does not detect staleness. The
template's `setup` and `force-prep` recipes carry the alias set, and
prep every brick declaring `:deps/prep-lib`.
See [ADR-0010](../../../docs/adr/0010-code-generation-via-prep-lib.md).
