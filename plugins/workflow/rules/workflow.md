# mono dev workflow

The conventions around committing and running things — keeping a branch
current against Renovate, the local gate before CI sees a commit, how a
`just` recipe is written — and the Tessl plugins an agent loads its
rules from.

## Pull from `main` before committing; let Renovate own dependency bumps

Pull and merge from `main` before committing — Renovate opens
dependency updates weekly, so a branch that hasn't pulled is likely
already behind — and resolve any conflict with a Renovate-managed file
(`deps.edn`, `.github/workflows/*`, `flake.lock`) before pushing. Never
manually bump a dependency version Renovate manages, except when a real
need requires a version Renovate hasn't yet caught up to. Stage a
user-initiated deletion or move with `git add` (`-u` or `-A`), never
`git rm` — reserve `git rm` for a deletion you are yourself initiating.
Never develop new features inside the user's untracked drafts; do
include them in workspace-wide operations (rename, dead-code cleanup,
regeneration) so the tree stays consistent, and report what was touched.
Cut a branch with `just gh-fresh-branch <name>`, commit and raise with
`just gh-commit-and-pr <title> [body]`, and land with `just gh-merge`:
each refuses rather than guesses — on a dirty tree, an existing branch
name, a commit on `main`, a path that looks like a credential, or a PR
that is closed or aimed at anything but `main`.
See [git-workflow](../../../docs/recipes/practices/git-workflow.md).

## The pre-commit hook formats and lints before CI sees it

Keep the git hooks checked in under `scripts/hooks/`: an extensionless
file is a hook, a `.sh` file is a helper a hook sources. `pre-commit`
formats staged Clojure files with zprint (auto-fix, restaged, configured
by `.zprint.edn` at 80 columns), lints them with clj-kondo (blocking,
configured by `.clj-kondo/config.edn`) and scans them with the semgrep
rules in `.config/semgrep/semgrep.yml`, a finding blocking unless the
site carries `;; nosemgrep: <rule>` with a reason; its steps are functions in
`scripts/hooks/lib.sh`, which a downstream workspace's hook sources and
surrounds with its own steps. `post-checkout` installs the Tessl rules a
fresh worktree lacks. Install with `just install-hooks`, once per clone
and again after a hook changes — the installed files are copies — from
the primary checkout, never a linked worktree. CI is the gate; the hook
is the fast path to it.
See [ADR-0012](../../../docs/adr/0012-pre-commit-hooks.md).

## A `just` recipe fails loudly and takes what it is given

Under `set -e`, `cmd && break`, `[[ test ]] && cmd` and a bare
`VAR=$(cmd)` whose command may fail end the recipe, not the line, so
consume a failure you expect with `if cmd; then break; fi` or `|| true`,
and read an instant exit with no output as `set -e` aborting before the
first `echo`. Capture a command's output into a variable before piping
it, so a denial is not read as an empty result. Use whatever the caller
supplied and discover only what they did not; pass the identity a
recipe acts as rather than discovering it, and stop rather than guess
where none is given. Declare an overridable variable with
`env_var_or_default`, which a workspace built on these bricks redeclares
in its root Justfile after the `import?` of the file that reads it;
declare a constant in the file that reads it, and in the root Justfile
— or a `vars.just` holding nothing else — where more than one does or
where it has to agree with one already there. A recipe lives in the
justfile for the domain it acts on and carries that domain's prefix,
with a private helper filed by the domain it is about; every recipe
carries a one-line comment naming its parameters and their fixed
values, which is what `just --list` shows; a file is ordered constants,
private helpers, then recipes in the order they are run, ad-hoc ones
last. A body carries no comment except one guarding an edit that would
break it — the why belongs in the recipe under `docs/`.
See [justfile-recipes](../../../docs/recipes/practices/justfile-recipes.md).
