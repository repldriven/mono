# mono system design

How a system is built from these bricks — messaging, its payloads,
system wiring, and code generation. The choices a workspace inherits by
depending on mono, as distinct from Polylith mechanics (`framework`) and
Clojure conventions (`idioms`).

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
Producers and consumers bind to a schema at registration, so a mismatch
is caught at startup, not in production.
See [ADR-0004](../../../docs/adr/0004-avro-for-message-payloads.md).

## System components are declared in YAML, registered in Clojure

Run component lifecycle through `donut.system`, with every system
defined in a YAML (or EDN) file the `system` and `env` bricks parse
before handing it to donut. Two layers: a component kind is registered
with `system/defcomponents` — its `:system/start` / `:system/stop` fns,
configuration schema and instance schema — in the brick that owns it;
the system file declares which components exist, of what kind, with
what configuration, wired by the tag literals `!system/component`,
`!system/ref`, `!system/local-ref`, `!system/required-component`,
`!profile`, `!env`, `!include` and `!strs`. `aero` resolves `!profile`
at load time, so a per-profile value or component group needs no source
branch. A required component (typically the HTTP `handler`) is a slot
the bootstrap caller fills with `assoc-in` before starting.
Testcontainers-backed infrastructure is declared in the same file
behind a profile, so tests boot through the production code path with a
different profile and group.
See [ADR-0007](../../../docs/adr/0007-system-as-data.md).

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
