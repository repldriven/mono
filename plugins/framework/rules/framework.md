# mono Polylith framework

How to use Polylith itself in a workspace built on these bricks — the
brick shape, the boundary discipline, and the assemblies built from
bricks. Independent of what the bricks do.

## Cross a brick boundary only through `interface.clj`

Reach another component through its `interface.clj`; a base does the
same, and has no interface of its own. Never require another brick's
`.core` / `.store` / `.domain`; if the symbol you need isn't on the
interface, add it there first. That reaching happens from your own
component's `core`/`domain`/`store`/etc. — never from your own
`interface.clj`, which requires only this component's own namespaces
(plus `clojure.core`) and nothing from any other brick, not even the
`error`/`utility` wrapper bricks. Never list a component in `deps.edn`:
Polylith resolves brick dependencies from interface references in
source, and a brick's `deps.edn` holds third-party libraries only.
See [components](../../../docs/recipes/code/components.md),
[bases](../../../docs/recipes/code/bases.md).

## Bases are application entry points

A base owns `-main` and `(:gen-class)` in its entry namespace, parses
CLI args, builds the system definition, and starts it. It accesses
components the same way any component reaches a peer — through
`interface.clj` — and bare-requires every brick whose system
multimethods need to extend at startup. Bases never depend on other
bases and share nothing between them except through components. A base
never owns a store: `component → base` is disallowed, so state behind
an entry point would be unreachable by every component; bare-requiring
a storage brick's interface to register its component kinds is
registration, not ownership.
See [bases](../../../docs/recipes/code/bases.md).

## Projects are pure config

A project in `projects/` is a `deps.edn` listing its components and
bases as `:local/root` paths — nothing else. Projects carry no Clojure
source and never define `-main` (a base does that); a deployable
project points its `:build` alias at `bases/build`, and a library
project (`mono-lib`, `mono-test-lib`) has no base at all and is what a
consumer names by `:deps/root`. A project MAY carry a `resources/`
folder for deployment-scoped files (`application.yml`, `logback.xml` /
`logback-test.xml`). Projects never depend on other projects. Every
project repeats the `org.clojure/clojure` pin, because the CLI makes
Clojure a direct dependency that nothing one level down can hold; a
library pinned *down* needs the newer copy excluded where it enters,
since a pin one level below a direct dependency loses.
See [projects](../../../docs/recipes/code/projects.md).

## Wrap every third-party library in exactly one brick

Give every third-party library — Java or Clojure, excluding
`clojure.core`, `clojure.string`, `clojure.set`, `clojure.walk`,
`clojure.spec.alpha` and the rest of core — exactly one brick as its
consumer, and reach it from any other brick through that wrapper's
`interface.clj`, never the library. Adding a library either creates a
new component or extends an existing one; when in doubt, a new
single-purpose component. `clj -M:poly libs` prints libraries as rows
and bricks as columns, and the principle is one X per row: a row with
more is a target for cleanup during ordinary development, consolidated
behind one component or accepted for a real reason — a discipline, not
a CI gate.
See [ADR-0011](../../../docs/adr/0011-one-component-per-third-party-library.md).
