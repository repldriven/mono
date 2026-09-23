# 12. Pre-commit hooks for formatting and linting

<!-- tessl-plugin: workflow -->

## Status

Accepted.

## Context

A Clojure codebase produces a steady supply of formatting drift and
lint issues. By the time CI catches them, the contributor has
already pushed; the feedback loop is minutes long and the diff
reviewers see is noisier than it needs to be.

We want fast local feedback for the cheap, mechanical issues —
formatting consistency and lint errors — so they never reach the
review stage.

The shortlist:

- **CI-only.** Catches everything, but the feedback loop is the
  full CI cycle. Cheap issues become expensive.
- **Editor-only.** Format-on-save in the contributor's editor.
  Works, but depends on each contributor's editor configuration;
  drift between editors leaves formatting inconsistent.
- **JavaScript-ecosystem hook managers** (husky, lefthook). Pull
  Node tooling into a Clojure project. Off-stack.
- **A checked-in shell script installed as a `git` pre-commit
  hook.** Fast local feedback, no extra tooling, the hook lives in
  the repo and runs the same way for everyone who installs it.

## Decision

We will keep the git hooks checked in under `scripts/hooks/`. An
extensionless file there is a hook, installed under its own name; a
`.sh` file is a helper a hook sources.

Two hooks ship, with how they are composed, installed and gated:

- **`pre-commit`** formats staged Clojure files with zprint —
  auto-fix, the reformatted files restaged, configured by
  `.zprint.edn` at 80 columns — lints them with clj-kondo, configured
  by `.clj-kondo/config.edn` including `lint-as` mappings for the
  workspace's macros, and scans them with the semgrep rules in
  `.config/semgrep/semgrep.yml`: a raw `throw`, a raw time or id
  primitive, `use-fixtures`, an entry point outside `bases/`, a broker
  client outside its wrapper brick. A lint error or a finding blocks
  the commit, unless the site carries `;; nosemgrep: <rule>` with a
  reason on the line above.
- **`post-checkout`** lays down the Tessl rules a fresh worktree does
  not carry: `.tessl/` is gitignored, so a new worktree has no
  `RULES.md` until `just tessl-plugins-install` has run there, and
  the hook runs it through the dev shell when a branch or worktree
  checkout finds none. It never fails the checkout.
- **Composition.** The steps `pre-commit` runs are shell functions in
  `scripts/hooks/lib.sh`, and the hook itself only sources the file
  and calls them in order. A workspace built on these bricks sources
  the same file from its own `pre-commit` and puts its own steps
  around them, in its own order, rather than copying the hook and
  editing it.
- **Installation** is `just install-hooks`, once per clone and again
  whenever a hook changes — the installed files are copies rather than
  symlinks, so an edit under `scripts/hooks/` changes nothing until it
  runs again. `.envrc` runs it on entering the primary checkout, which
  is what arms them. There is one hooks directory per clone, so an
  install covers every worktree, and the recipe refuses to run from a
  linked worktree, whose checkout is whatever commit a work item
  landed on.
- **The gate.** The hook is a local convenience. The actual gate is
  CI, which runs the same checks. Bypassing the hook
  (`git commit --no-verify`) produces a commit that CI will reject if
  it has formatting or lint issues.

## Consequences

Easier:

- Fast feedback. Formatting drift and lint errors are caught
  before commit, not minutes later in CI.
- Reviews focus on the change. Mechanical issues don't reach the
  review stage.
- The codebase stays consistently formatted because zprint runs on
  every commit, not on every contributor's whim.
- The hook is checked in. Everyone who installs it runs the same
  script with the same configurations.
- A downstream hook is composed, not forked. The generic steps come
  from `lib.sh` at whatever version the workspace pins, so a fix to
  formatting or linting reaches every consumer without each one
  re-editing its hook.

Harder:

- Installation is a manual step per clone. Easy to forget;
  `.envrc` covers the primary checkout and the cost of forgetting
  elsewhere is "CI fails on the next push," not silent breakage.
- clj-kondo lints only the staged files. A change that breaks an
  unstaged caller passes the hook, and is caught by `just lint` over
  the whole workspace and by CI.
- Hooks are bypassable (`--no-verify`). The hook is a discipline,
  not enforcement. CI is the gate.
- The hook depends on `zprint`, `clj-kondo` and `semgrep` being
  available locally. All three come in via the dev shell, but a fresh
  setup needs them before the hook is useful.
