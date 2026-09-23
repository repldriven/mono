# 11. One component per third-party library

<!-- tessl-plugin: framework -->

## Status

Accepted.

## Context

Polylith enforces interface boundaries *between* components, but
inside a component nothing stops you from `:require`-ing any
third-party library you like. Without further discipline that
quickly produces a codebase where, say, every component touches the
YAML library directly, or a dozen places call into the HTTP client.
Swapping or upgrading the library then ripples across all of them.

We want the property the message-bus abstraction (ADR-0003) gave us
to apply more broadly: every third-party library is encapsulated by
exactly one component. Other components depend on the abstracting
component, not on the library. Library swaps, upgrades, and bug
fixes become contained changes — change one place, the rest of the
system is unchanged.

The shortlist of how to enforce it:

- **No discipline.** Each component imports what it needs. Library
  upgrades and swaps become cross-cutting changes. Rejected.
- **Wrapper libraries (separate JARs).** Heavier than necessary —
  Polylith components already do this job; building separate JARs
  duplicates the work.
- **Service interfaces (HTTP / gRPC) over every library.** Useful
  at process boundaries; absurdly heavy for in-process calls.
- **Polylith components as the wrapper.** Each library has exactly
  one component as its sole user; that component's `interface.clj`
  is the project's API to the library.

## Decision

Every third-party library in the workspace — Java or Clojure,
excluding standard core Clojure libraries — has exactly one
Polylith brick as its consumer. Other bricks depend on the wrapping
component's `interface.clj`, never on the library directly.

The parts of the decision:

- **The exception.** Core Clojure libraries (`clojure.core`,
  `clojure.string`, `clojure.set`, `clojure.walk`,
  `clojure.spec.alpha`, and so on) are the project's lingua franca
  and not subject to this rule.
- **Adding a new library** either creates a new component or extends
  an existing one; the placement question is "what is the conceptual
  boundary this library sits behind." When in doubt, a new
  single-purpose component is the safer answer.
- **The visual check.** `clj -M:poly libs` prints a matrix with
  libraries as rows and bricks (components and bases) as columns. The
  principle is **one X in each library row.** Reality is messier —
  over the lifetime of a codebase, libraries occasionally find their
  way into more than one brick (the `buddy-sign`, `sieppari` and
  `java.data` rows each have more than one X). We treat such rows as
  targets for cleanup during ordinary development: consolidate the
  import behind one component, or, where there is a real reason for
  the exception, accept it and move on. The discipline is the
  principle, not a CI gate.
- **Worked examples** already in the codebase: `avro` wraps
  Lancaster, `http-client` wraps http-kit, `jdbc` wraps next.jdbc,
  `vault` wraps `amperity/vault-clj`, `error` wraps `de.otto/nom`,
  and `env` wraps aero and clj-yaml.

## Consequences

Easier:

- Library swaps and upgrades are contained. The message-bus →
  channels / broker swap (ADR-0003) is a worked example; a future
  HTTP-client swap would be another.
- The wrapping component's `interface.clj` is the project's
  vocabulary. Callers see kebab-case Clojure functions returning
  anomalies (ADR-0005), not whatever idiom the library happens to
  use.
- Error-mapping happens once per library — at the component
  boundary, where exceptions become anomalies via `error/try-nom`
  / `error/try-nom-ex`. No component leaks library-specific
  exceptions to its callers.
- Library upgrades, security patches, and dependency conflicts are
  scoped to one brick. Renovate PRs touch a known small set of
  bricks, not the whole workspace.
- New contributors learn the component interfaces, not the
  libraries underneath.
- The matrix surfaces drift. `clj -M:poly libs` makes any library
  used by more than one brick visible at a glance, which is the
  basis for periodic review and reviewer-side noticing during
  feature work.

Harder:

- The wrapping component has to expose enough of the library to
  satisfy real callers. Too thin and callers route around it; too
  thick and it becomes a re-implementation. Calibration is
  per-library.
- Some libraries are very large (the Pulsar client); the wrapping
  component grows accordingly. "One component per library" is not
  always "one small component per library."
- A new library that conceptually overlaps an existing component
  is a placement question. The default — a new component — is
  sometimes overkill; the alternative — extending an existing
  component — sometimes overstrains it. Judgement call per case.
- Drift happens. A library can find its way into a second brick
  during a feature push or a quick fix; some rows in the matrix
  already show this. The discipline is to notice during code
  review or a periodic audit and consolidate before the second
  X becomes a third.
- Wrapping has a cost. Some libraries are simple enough that a
  wrapper feels like ceremony. Worth doing anyway, because the
  rule loses its teeth as soon as exceptions become routine.

A workspace built on these bricks applies the same rule to its own.
