# 7. System-as-data via donut.system and YAML

<!-- tessl-plugin: design -->

## Status

Accepted.

## Context

A system built from these bricks comprises a lot of moving parts:
database connections, message-bus producers and consumers, an HTTP
server, command processors, event processors, telemetry, vault. Each
has a lifecycle — start, configure, wire to its dependencies, stop
cleanly on shutdown. Each runs in different shapes across profiles
(`dev`, `test`, and `default` for everything else) and across
deployment topologies (everything in one process, or one process per
service group).

We need a single way to express:

1. The set of components that make up a running system.
2. How they depend on each other.
3. How they configure differently per environment.
4. How tests can swap pieces in and out without re-implementing the
   whole bootstrap.

The shortlist:

- **Hand-rolled lifecycle** — `(do (start-db) (start-broker) ...)`.
  Fine for tiny apps. Doesn't scale: dependency order maintained
  manually, no test composition story, profile differences become
  source branches.
- **Alessandra Sierra's Component.** Venerable. Components are records
  with a `start`/`stop` protocol. The system itself is still Clojure
  source, with no obvious place to put per-environment values short
  of code branching.
- **Mount.** Global state; doesn't compose; harder to test in
  isolation.
- **Integrant.** Data-driven, multimethods keyed on component
  keywords. Closer in spirit to what we want. The system map is
  Clojure data, but it lives in source files; externalising to a
  config file isn't built-in.
- **`donut.system` plus a YAML/EDN reader layered on top.** donut's
  contract is a data-driven graph with reverse-topsort start and
  topsort stop signals; the reader pushes the system definition out
  of source code into a config file that operators (and tests, and
  profiles) can edit as data.

A presentation walking through how the bricks assemble all this lives
at
[docs/slides/systems-as-data/slides.md](../slides/systems-as-data/slides.md);
this ADR captures the decision rather than the walk-through.

## Decision

We will use [donut.system](https://github.com/donut-party/system)
for component lifecycle, and define every system in a YAML (or EDN)
configuration file parsed by the `system` and `env` bricks before
being handed to donut.

Two layers, one per concern:

- **Implementation in Clojure.** Each component kind is registered
  via `system/defcomponents` with its `:system/start` /
  `:system/stop` functions, configuration schema, and instance
  schema. This lives in the brick that owns the component.
- **Declaration in YAML.** The system file lists which components
  exist in this system, what kind each is, what configuration each
  takes, and how they wire to one another. Tag literals
  (`!system/component`, `!system/ref`, `!system/local-ref`,
  `!system/required-component`, `!profile`, `!env`, `!include`,
  `!strs`) cover the wiring vocabulary.

Profiles are resolved by `aero` at load time. `!profile` selects
values — or whole component groups — per profile, with no source
branching.

Required components (typically the HTTP `handler`) are slots: the
YAML marks them `!system/required-component`, the bootstrap caller
injects the value via `assoc-in` before starting the system.

Testcontainers-backed infrastructure (a database, the message bus,
Vault, and so on) is declared in the same system file as everything
else, gated behind a profile. Tests boot through the same code path
as production — just with a different profile and a different
database / message-bus group.

## Consequences

Easier:

- The system is a value. It can be `tap>`-ed into Portal, walked,
  diff'd, and overridden component-by-component.
- Tests and production share a bootstrap path. The differences live
  in profile values and group overrides, not in parallel
  implementations.
- Adding a new component is: write the start/stop fn, register it
  via `defcomponents`, reference it in YAML. No bootstrapping
  ceremony.
- Dependency order is derived from refs, not maintained by hand.
  Reverse-topsort on start, topsort on stop. Cycles are detected at
  load.
- Operators and reviewers can read the system shape without reading
  Clojure.
- The bricks register component kinds for the full stack (`jdbc`,
  `message-bus`, `server`, `command-processor`, `event-processor`,
  `scheduler`, `telemetry`, `vault`, and others). A downstream system
  is assembled mostly from already-wired primitives, adding kinds
  only for what it owns.

Harder:

- The reader machinery (tag literals, profile resolution, the YAML →
  donut translation) is custom. The `system` and `env` bricks are
  non-trivial. New contributors must learn what `!system/component`,
  `!system/ref`, and `!profile` mean before they can read a system
  file confidently.
- The two-layer split (YAML declares, Clojure implements) means a
  new component touches both. Once internalised this is natural; up
  front it is an extra step.
- YAML's ambiguities (string vs number, nested types, key quoting)
  bite occasionally. `!strs` is the escape hatch for config a
  library wants string-keyed; similar escape hatches will appear
  elsewhere.
- Debugging a system that fails to start requires understanding both
  the YAML wiring and donut's signal propagation. Less obvious than
  a stack trace from a single `(start-y)` call.
