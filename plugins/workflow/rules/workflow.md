# mono dev workflow

The local gate before CI sees a commit, and the Tessl plugins an agent
loads its rules from.

## The pre-commit hook formats and lints before CI sees it

Keep the git hooks checked in under `scripts/hooks/`: an extensionless
file is a hook, a `.sh` file is a helper a hook sources. `pre-commit`
formats staged Clojure files with zprint (auto-fix, restaged, configured
by `.zprint.edn` at 80 columns) and lints them with clj-kondo (blocking,
configured by `.clj-kondo/config.edn`); its steps are functions in
`scripts/hooks/lib.sh`, which a downstream workspace's hook sources and
surrounds with its own steps. `post-checkout` installs the Tessl rules a
fresh worktree lacks. Install with `just install-hooks`, once per clone
and again after a hook changes — the installed files are copies — from
the primary checkout, never a linked worktree. CI is the gate; the hook
is the fast path to it.
See [ADR-0012](../../../docs/adr/0012-pre-commit-hooks.md).
