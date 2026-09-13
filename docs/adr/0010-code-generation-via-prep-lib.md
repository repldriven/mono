# 10. Code generation via `:deps/prep-lib` and co-located `build.clj`

<!-- tessl-plugin: design -->

## Status

Accepted.

## Context

Some bricks need code generated from a source artefact rather than
hand-written: a schema compiler's output — protobuf definitions, gRPC
stubs — or bindings to a published API schema. Nothing in the bricks
here generates code; a workspace built on them will, and it needs a
convention before the first such brick appears.

Generation has well-known failure modes:

- **Committed generated code** goes stale, drifts across
  contributors and machines, and clutters every diff with hundreds
  of auto-gen lines.
- **Build-system plugins** (Maven, Gradle) drag a second build DAG
  alongside Clojure's deps tooling, with its own knowledge tax.
- **Custom run-scripts** (shell, Babashka) work but live outside the
  well-known `clj -X:deps prep` idiom that Clojure tooling already
  understands.

Clojure's `tools.deps` ships a `:deps/prep-lib` mechanism precisely
for this case: a brick declares it needs preparation, the work is
defined in code, and the standard deps tooling runs it on demand.

## Decision

We will use Clojure's standard `:deps/prep-lib` mechanism for all
code generation. The pattern, applied per brick:

- The brick's `deps.edn` declares `:deps/prep-lib` with an `:fn` and
  the `:ensure` path that signals the prep is up-to-date.
- A `build.clj` co-located in the brick implements the generation
  logic. The function named in `:fn` is the entry point.
- Generated code lands in a `gen/` source folder inside the brick.
- The `gen/` folder is gitignored *locally* via a per-brick
  `.gitignore`. Generated code is never committed.
- Regeneration is a deliberate command:

  ```
  clj -X:deps prep :aliases '[:dev]'
  ```

  After source-schema changes, force regeneration:

  ```
  clj -X:deps prep :aliases '[:dev]' :force true
  ```

Every brick is on the `:dev` alias, or on one of a workspace's domain
aliases, in the top-level `deps.edn`, so the alias set generation
needs is the one everything else already uses. The template's
`setup` and `force-prep` recipes carry that set, and prep every brick
declaring `:deps/prep-lib` — a no-op until one does.

## Consequences

Easier:

- One command regenerates everything that needs it. `clj -X:deps
  prep` is the same shape across bricks; the tooling does the rest.
- Generated code stays out of git. Reviews show source-schema diffs,
  not the thousands of auto-gen lines downstream of them.
- Consistency across machines and contributors: the tool is the
  Clojure deps version we all run, not a vendored binary or a
  Maven plugin.
- Standard mechanism. New Clojure contributors recognise the
  `:deps/prep-lib` shape; no custom build vocabulary to learn.

Harder:

- Prep is a manual or first-time-build step. New contributors must
  remember to run it; CI must run it before tests. Forgetting it
  produces runtime errors that look like real bugs.
- The `:force true` flag is non-obvious. After a source-schema
  change, the prep marker may indicate everything is up-to-date
  even though the inputs changed. Forgetting `:force` produces
  stale generated code with no warning.
- Each brick that needs generation ships its own `build.clj`. There
  is light duplication between them; a shared helper in a workspace's
  build base can reduce it, but each brick still needs an entry
  point.
- The `gen/` folder being gitignored means IDEs and REPLs need it
  on disk to navigate or autocomplete. First-time setup includes a
  prep step.
