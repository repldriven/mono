# mono dev workflow

The conventions around committing and running things — keeping a branch
current against Renovate, the local gate before CI sees a commit, how a
`just` recipe is written — and the Tessl plugins an agent loads its
rules from.

## Pull from `main` before committing; let Renovate own dependency bumps

Pull and merge from `main` before committing, and re-run the tests
after the merge; resolve any conflict with a Renovate-managed file
(`deps.edn`, `.github/workflows/*`, `flake.lock`) before pushing. Never
manually bump a dependency version Renovate manages, except when a real
need requires a version Renovate hasn't yet caught up to — then check
for an open Renovate PR first, and keep the bump narrowly scoped so the
next Renovate PR can close or merge cleanly. Stage a user-initiated
deletion or move with `git add`, never `git rm`, which is for a
deletion you are yourself initiating, and stage both ends of a move so
git records it as a rename. Never develop new features inside the
user's untracked drafts; do include them in workspace-wide operations
(rename, dead-code cleanup, regeneration) so the tree stays consistent,
and report what was touched. Cut a branch, commit and raise the PR, and
land it through the `just` recipes, each of which refuses rather than
guesses — on a dirty tree, an existing branch name, a commit on `main`,
nothing to commit, a path that looks like a credential, or a PR that is
not open or aimed at anything but `main`.
Commands: `just gh-fresh-branch`, `just gh-commit-and-pr`, `just
gh-merge`.
See [git-workflow](../../../docs/recipes/practices/git-workflow.md).

## The pre-commit hook formats and lints before CI sees it

Keep the git hooks checked in under `scripts/hooks/`: an extensionless
file is a hook, installed under its own name, a `.sh` file a helper a
hook sources. `pre-commit` formats staged Clojure files — `cond` pairs laid
out by `cond_pairs.clj` under babashka, then zprint (auto-fix,
restaged, configured by `.zprint.edn` at 80 columns) — lints
them with clj-kondo (configured by `.clj-kondo/config.edn`, `lint-as`
mappings for the workspace's macros included) and scans them with the
semgrep rules in `.config/semgrep/semgrep.yml` — a raw `throw`, a raw
time or id primitive, `use-fixtures`, an entry point outside `bases/`,
a broker client outside its wrapper brick; a lint error or a finding
blocks the commit unless the site carries `;; nosemgrep: <rule>` with a
reason on the line above. Its steps are functions in
`scripts/hooks/lib.sh`, which the hook only sources and calls in order,
and which a downstream workspace's own `pre-commit` sources and
surrounds with its own steps rather than copying the hook.
`post-checkout` runs `just tessl-plugins-install` through the dev shell
when a branch or worktree checkout finds no `RULES.md`, since `.tessl/`
is gitignored, and never fails the checkout. Install with
`just install-hooks`, once per clone and again after a hook changes —
the installed files are copies, not symlinks — from the primary
checkout, where `.envrc` runs it, never a linked worktree; one hooks
directory per clone covers every worktree. CI is the gate and runs the
same checks: the hook is a local convenience, and `--no-verify`
produces a commit CI rejects for a formatting or lint issue.
See [ADR-0012](../../../docs/adr/0012-pre-commit-hooks.md).

## A `just` recipe fails loudly and takes what it is given

Under `set -e`, `cmd && break`, `[[ test ]] && cmd` and a bare
`VAR=$(cmd)` whose command may fail end the recipe, not the line, so
consume a failure you expect with `if cmd; then break; fi` or `|| true`,
and read an instant exit with no output as `set -e` aborting before the
first `echo`. Capture a command's output into a variable before piping
it, so a denial is not read as an empty result. Use whatever the caller
supplied and discover only what they did not — a lookup for a value the
caller named fails where the argument would have worked; pass the
identity a recipe acts as rather than discovering it, name the fallback
where a recipe can act as you and needs one, and stop rather than guess
where none is given. Declare an overridable variable with
`env_var_or_default`, which a workspace built on these bricks
redeclares in its root Justfile, under `set allow-duplicate-variables`,
after the `import?` of the file that reads it; declare a constant in
the file that reads it, and in the root Justfile — or a `vars.just`
holding nothing else — where more than one does or where it has to
agree with one already there. Name a recipe for what it acts on, not
what it is made of, and file it in the justfile for that domain with
the domain's prefix — `install-hooks` stays unprefixed, since every
clone's `.envrc` and `post-checkout` and every downstream workspace
call it by that name; a private helper is filed by the domain it is
about, whoever calls it, and takes what varies as an argument. Every
recipe carries a one-line comment naming its parameters and their
fixed values, which is what `just --list` shows; a file is ordered
constants, private helpers, then recipes in the order they are run,
ad-hoc ones last. A body carries no comment except one guarding an edit
that would break it — the why belongs in the recipe under `docs/`.
See [justfile-recipes](../../../docs/recipes/practices/justfile-recipes.md).
