# CLAUDE.md

mono is a Clojure component library and reference implementation for
composing systems from independently testable bricks, organised as a
[Polylith](https://polylith.gitbook.io/polylith) workspace. A workspace
built on it consumes `projects/mono-lib` as a pinned git dependency and
lays the ADRs, recipes, plugins and hooks here down beside its own. See
[readme.md](readme.md) for the bricks and how to depend on them.

## Topic router

CLAUDE.md is the routing layer. Every `docs/recipes/*/*.md` and
`docs/adr/*.md` file below is labeled `<!-- tessl-plugin: <name> -->`,
and that plugin's rule (always loaded via `AGENTS.md`) already distills
its `## Rules` / `## Decision` — you don't need to open the doc to
rediscover that. Open it for the *why* behind the rule instead: Context,
Consequences, Discussion.

`docs/prd/` and `docs/tdd/` are the exception: nothing distills them,
so open those in full before non-trivial work on their topic.

### Code

- **Clojure code style** — naming, requires, destructuring, anon fns,
  `cond->`, `let`-binding format, ID generation (`util/uuidv7`),
  timestamps (`util/now`), interceptor short-circuit
  (`sieppari.context/terminate`).
  See [code-style.md](docs/recipes/code/code-style.md).
- **Common helpers** — when to add a helper to `utility`, when to
  re-export from a library, the convergence rule.
  See [common-helpers.md](docs/recipes/code/common-helpers.md).
- **Component interfaces and docstrings** — `interface.clj` is the
  documentation surface; impl files stay bare.
  See [ADR-0015](docs/adr/0015-comments-and-docstrings.md) and
  [components.md](docs/recipes/code/components.md).
- **Error handling** — anomalies at component boundaries; never throw
  from `interface.clj`; `error/try-nom` and `error/nom->` at library
  edges. See [ADR-0005](docs/adr/0005-error-handling-with-anomalies.md)
  and [error-handling.md](docs/recipes/code/error-handling.md).
- **Data shapes** — kebab-case keyword keys throughout, with a code an
  external standard defines as the exception.
  See [ADR-0006](docs/adr/0006-kebab-case-keyword-keys.md).

### Architecture

- **Brick boundaries** — `interface.clj` discipline, one brick per
  third-party library.
  See [components.md](docs/recipes/code/components.md) and
  [ADR-0011](docs/adr/0011-one-component-per-third-party-library.md).
- **Bases and projects** — entry points, deployable projects, the
  library projects a consumer names by `:deps/root`, library pinning.
  See [bases.md](docs/recipes/code/bases.md) and
  [projects.md](docs/recipes/code/projects.md).
- **System wiring** — `system/defcomponents`, `system.clj` vs `system/`
  folder, the test-bundle pattern, naming shared resource components.
  See [system-components.md](docs/recipes/code/system-components.md)
  and [ADR-0007](docs/adr/0007-system-as-data.md).
- **System configurations** — YAML system definitions, profiles,
  `!system/component` / `!system/ref` / `!env`, required-component
  injection. See
  [system-configurations.md](docs/recipes/code/system-configurations.md).
- **Messaging** — the message bus behind an abstraction, Avro
  payloads, and how a subscription fans out and stops.
  See [ADR-0003](docs/adr/0003-message-bus-abstraction.md),
  [ADR-0004](docs/adr/0004-avro-for-message-payloads.md) and
  [message-bus.md](docs/tdd/message-bus.md).
- **Code generation** — the prep-lib convention, for a workspace that
  needs it; no brick here generates code.
  See [ADR-0010](docs/adr/0010-code-generation-via-prep-lib.md).
- **Mail** — sending email through the mail server an installation
  names, and the `smtp` brick that will do it.
  See [mail.md](docs/prd/mail.md) and [smtp.md](docs/tdd/smtp.md).

### Tests

- **Test systems** — `with-test-system`, `nom-test>`, no
  `use-fixtures`, per-brick test config, what bounds the runner's
  parallelism. See [test-system.md](docs/recipes/test/test-system.md).
- **Testcontainers** — the three-layer pattern, extractors in the
  relevant brick's `system/` folder.
  See [testcontainers.md](docs/recipes/test/testcontainers.md).

### Operations

- **Git workflow** — merge `main` before committing, let Renovate own
  dependency bumps, stage user-initiated deletions and moves with
  `git add` not `git rm`, include the user's untracked drafts in
  workspace-wide ops.
  See [git-workflow.md](docs/recipes/practices/git-workflow.md).
- **Git hooks** — what `pre-commit` and `post-checkout` run, and how a
  workspace built on these bricks composes its own from `lib.sh`.
  See [ADR-0012](docs/adr/0012-pre-commit-hooks.md).
- **Justfile recipes** — the `set -e` shapes that abort silently,
  capturing before piping, where a constant is declared, what a comment
  in a body is for.
  See [justfile-recipes.md](docs/recipes/practices/justfile-recipes.md).
- **Writing docs** — wrap at 80, link hygiene, mermaid, tone; the
  `check-docs` skill that verifies them; then what each kind of
  document is made of.
  See [writing-docs.md](docs/recipes/practices/writing-docs.md),
  [writing-recipes.md](docs/recipes/practices/writing-recipes.md),
  [writing-adrs.md](docs/recipes/practices/writing-adrs.md),
  [writing-tdds.md](docs/recipes/practices/writing-tdds.md) and
  [writing-prds.md](docs/recipes/practices/writing-prds.md).
- **Tessl plugins** — the rules an agent loads, the roots they install
  from, profiles. See [plugins/README.md](plugins/README.md).

## What this workspace publishes

- `projects/mono-lib` and `projects/mono-test-lib` are published
  artifacts, not deployables. They have no base, list curated bricks as
  `:local/root` deps, and are consumed downstream as git deps via
  `:deps/root`. Adding or changing a brick in either is release-visible:
  consumers pin a sha and there is no snapshot channel, so it requires a
  new tag. Their dep keys are qualified
  (`com.repldriven.mono.components/env`) to avoid colliding with a
  consumer's own keys, and they are listed in `workspace.edn` with
  `:necessary` because a base-less project trips warning 207.
- Both roots ship under ONE lib symbol, `com.repldriven/mono`, differing
  only by `:deps/root`. tools.deps checks out a git dep once per lib
  symbol, so two symbols would mean two checkouts and two irreconcilable
  paths for every component the roots share. Because `:extra-deps`
  merges by lib symbol, a consumer's `:test` alias REPLACES the runtime
  root with the test one, so `mono-test-lib` MUST stay a superset of
  `mono-lib` — never prune a component from it. The release workflow
  asserts this.
- A component published in `mono-lib` MUST be self-contained: it MUST
  NOT read files relative to the workspace root, since a consuming
  workspace has no such files.
- `template/` is a deps-new template that scaffolds a workspace wired
  to `mono-lib`. It sits outside the Polylith directories, so `poly`
  ignores it. Starter bricks are **not** committed here; they are copied
  from mono at generation time and namespace-rewritten. Only segments
  listed in `starter.edn` are rewritten, so references to bricks that
  come from `mono-lib` keep pointing at `com.repldriven.mono.*`. Verify
  with `just template-test`.
- The ADRs, recipes, plugins, hook library, semgrep rules and justfiles
  here are laid down in a consuming workspace at the sha it pins, so a
  change to any of them is release-visible too.

## Common commands

```bash
# Run every brick's tests in every project, capped to Docker's CPUs.
just test

# Run the tests of one or more bricks in the development project.
clojure -M:poly test brick:<brick-name> project:dev
clojure -M:poly test brick:<brick1>:<brick2> project:dev

# Prepare a fresh worktree: prep every brick that declares :deps/prep-lib.
just setup

# Install the git hooks, once per clone and again after a hook changes.
just install-hooks

# Install the Tessl plugins from the working tree, and check them.
just tessl-plugins-install
just tessl-plugins-check

# Generate a throwaway workspace from the template and verify it.
just template-test
```

@AGENTS.md
