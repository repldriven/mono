# mono Polylith framework

How to use Polylith itself in a workspace built on these bricks — the
brick shape, the boundary discipline, and the assemblies built from
bricks. Independent of what the bricks do.

## Cross a brick boundary only through `interface.clj`

`interface.clj` is a component's public API: it delegates to
namespaces in the same component and implements no logic itself.
Reach another component through its `interface.clj`; a base does the
same, and has no interface of its own. Never require another brick's
`.core` / `.store` / `.domain`; if the symbol you need isn't on the
interface, add it there first. That reaching happens from your own
component's `core`/`domain`/`store`/etc. — never from your own
`interface.clj`, which requires only this component's own namespaces
and nothing from any other brick, not even a library-wrapper brick
like `error` or `utility`. Never list a component in `deps.edn`:
Polylith resolves brick dependencies from interface references in
source, and a brick's `deps.edn` holds third-party libraries only,
each in exactly one brick. Define every protocol in the brick's
`protocol.clj`, a namespace with no `<ns>-test` of its own — never in
`core.clj` beside a `core_test.clj`.
See [components](../../../docs/recipes/code/components.md),
[bases](../../../docs/recipes/code/bases.md).

## Bases are application entry points

Each runnable application has exactly one base. A base owns `-main`
and `(:gen-class)` in its entry namespace; `-main` parses CLI args and
calls `start`, which builds the system definition from a YAML config,
injects any `!system/required-component` slots and calls
`system/start`. It accesses components the same way any component
reaches a peer — through `interface.clj` — and bare-requires every
brick whose system multimethods need to extend at startup; tests may
consolidate those bare-requires into a single `test/.../system.clj`.
Bases never depend on other bases and share nothing between them
except through components. Nothing here composes bases: a workspace
built on these bricks that composes several into one process — for
local development, or an end-to-end test rig — does it through one
designated aggregator that reaches each composed base by a declared
surface, and writes that convention down as its own. A base never
owns a store: `component → base` is disallowed, so state behind an
entry point would be unreachable by every component; bare-requiring a
storage brick's interface to register its component kinds is
registration, not ownership.
See [bases](../../../docs/recipes/code/bases.md).

## Projects are pure config

A project in `projects/` is a `deps.edn` listing its components and
bases as `:local/root` paths — nothing else. Projects carry no Clojure
source and never define `-main` (a base does that). A deployable
project points its `:build` alias at `bases/build`, and its `:test`
alias carries the bricks only tests need — `test-resources`,
`test-system`, `testcontainers` — and the runner,
`external-test-runner`. A library project (`mono-lib`, `mono-test-lib`)
has no base at all and is what a consumer names by `:deps/root`: it
carries no `:build` alias, an empty `:paths`, dep keys qualified with
the workspace top namespace so a consumer's own key cannot clash, and
`logback-test.xml` off `:paths` and on the `:test` alias's
`:extra-paths`, since a library's `:paths` join every consumer's
classpath and logback prefers a `logback-test.xml` found anywhere on
it; `mono-test-lib` is a superset of `mono-lib`, and the release
workflow asserts it. A project MAY carry a `resources/` folder for
deployment-scoped files (`application.yml`, `logback.xml` /
`logback-test.xml`); the system YAML MAY sit under the base's own
`resources/<base>/` instead, and the runtime names it with
`--config-file` and `--profile`. Profiles (`:dev`, `:test`, `:prod`)
are `aero` `!profile` tags inside that YAML, never separate files.
Projects never depend on other projects. Declare a version several
bricks or projects must agree on once — a project-level pin in `:deps`
for one deployable, a shim under `deps/` referenced by `:local/root`
under a `pin/` key for every project that references it. Every
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
