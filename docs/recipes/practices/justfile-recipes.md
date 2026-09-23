# Justfile recipes

<!-- tessl-plugin: workflow -->

## Problem

You are writing or changing a `just` recipe: where it lives, what it
takes, how it reads, and how it fails.

## Solution

Fail loudly, take what the caller knows rather than discovering it, and
say the rest in the recipe under `docs/` instead of in the file.

### set -e aborts more than it looks

Under `set -e`, each of these ends the *recipe*, not the loop or the
line:

- `cmd && break` — the list returns `cmd`'s status, so a loop written
  to wait through a transient condition aborts on its first failure.
- `VAR=$(cmd)` — a bare assignment whose command substitution fails.
  `docker exec <name> pg_isready` exits 1 while postgres is starting,
  so a loop waiting *for* it aborts the moment it is not ready.
- `[[ test ]] && cmd` — the same shape as the first.

The symptom is an instant exit with no output, because the recipe dies
before reaching its own `echo`. It then passes on the next run, once
the thing exists.

Consume the failure instead: `if cmd; then break; fi`, or `|| true`
where emptiness is handled on the following lines. `realworld-hurl`
waits for postgres and the service with `cmd && break` loops, and can
only because it sets `-uo pipefail` and not `-e`; a retry loop that
looks fine may never have retried, because its first attempt had always
happened to succeed.

### Capture before printing

`CMD | sed ...` takes `sed`'s exit status, so a denial prints nothing
and reads as an empty result. Capture into a variable, test it, then
print. `lint-branch` captures the changed files first and says so when
there are none, rather than piping an empty list into kondo.

### Do not rediscover what you were given

A recipe that takes a name and then discovers the thing anyway ignores
what the caller supplied — and discovery can *fail* where the argument
would have succeeded, because it refuses when more than one is visible.
`nvd project` builds the named project's classpath, and the dev
classpath when given none; it never guesses which project the caller
meant.

The identity a recipe acts as is the same thing. A recipe that reaches
for a particular account works only while that account exists and can
be assumed, which couples it to one moment. Where a recipe can act as
you and needs a fallback, the fallback is named too, and with none
given it stops rather than guessing.

### Variables

`VAR := "default"` ignores the environment. Declare anything an
operator may need to override as
`env_var_or_default("VAR", "default")`, which leaves `just --set` as
the per-run override and the environment as the per-shell one. That is
also what lets a workspace built on these bricks override a default
that is only ours: `TESSL_PLUGIN_ROOTS`, `SEMGREP_CONFIGS`,
`GUARDRAILS` and `NVD_CONFIG` are each redeclared in that workspace's
root Justfile, under `set allow-duplicate-variables`, after the
`import?` of the file that reads them.

Justfile imports share one namespace, so where a constant is declared
is about where a reader looks. Declare it in the file that reads it,
and in the root Justfile — or a `vars.just` holding nothing else —
where more than one does, or where it has to agree with one that is
already there. `DOMAIN_ALIASES` is read by `setup`, `nvd` and the REPL
recipes, and `POLY_PROFILES` is the same thing in poly's spelling, so
they sit together and a change to one is visibly a change to both.

A private helper goes with the domain it is about, whoever calls it,
and takes what varies as an argument.

### Names

Name a recipe for what it acts on, not for what it is made of, so the
list reads as a set of actions.

The name and the file agree: a recipe lives in the justfile for its
domain and carries that domain's prefix — `gas-` in `gas.just`,
`tessl-` in `tessl.just`, `nvd` in `nvd.just`. `just --list` is one
flat list, so the prefix is what groups a domain's recipes in it, and
the file is where somebody looks for the one they half-remember. A
recipe filed by what it uses rather than by what it acts on is findable
by neither.

### One line each, naming the parameters

Every recipe carries a one-line comment above it, and that line is what
`just --list` shows. Name the parameters in it, and the values a
parameter takes where they are fixed.

```
;; Bad
# Link a profile.
# Scan the tree.

;; OK
# Link one profile's rules from .tessl/RULES.md. name: a profile in plugins/profiles, `default` among them.
# Scan for semgrep guardrails (no args = all bricks, or pass paths).
```

### A comment in a body guards an edit

A recipe body carries no commentary except where a reader would
otherwise make a specific edit that breaks it: replacing the copy in
`install-hooks` with a symlink, dropping the `|| true` that absorbs
grep's empty match in `lint-branch`, moving the profile re-lay in
`tessl-plugins-install` ahead of the install that rewrites it. Each is
one or two lines and says what not to do.

Everything else — why an identity holds what it holds, what a
constraint prevents, which root to install from and when — is the
recipe's under `docs/`, where somebody reading about the act will find
it. A justfile that explains itself is a second copy of that recipe,
and it drifts.

### Order is call order

Constants first, then private helpers, then the recipes in the order
somebody runs them, with the ad-hoc ones last. `tessl.just` declares
the roots, installs, lays the profile down, reports what is loaded, and
ends with the check — which CI calls and a person rarely does.

## Rules

**MUST:**

- Consume a failure you expect: `if cmd; then break; fi`, or `|| true`
  where emptiness is handled explicitly.
- Capture a command's output into a variable before piping it, so a
  denial is not read as an empty result.
- Use whatever the caller supplied, and discover only what they did
  not.
- Declare an overridable variable with `env_var_or_default`. A
  workspace built on these bricks redeclares it in its root Justfile,
  under `set allow-duplicate-variables`, after the `import?` of the
  file that reads it.
- Name a recipe for what it acts on, not for what it is made of, and
  put it in the justfile for that domain, prefixed with the domain's
  name. The prefix is what groups it in `just --list`.
- Give every recipe a one-line comment naming its parameters, and the
  values a parameter takes where they are fixed. That line is what
  `just --list` shows.
- Order a file as constants, private helpers, then recipes in the order
  they are run, with the ad-hoc ones last.
- Declare a constant in the file that reads it, and in the root
  Justfile — or a `vars.just` holding nothing else — where more than
  one does or where it has to agree with one already there. Put a
  private helper with the domain it is about, whoever calls it, and
  have it take what varies as an argument.
- Pass the identity a recipe acts as rather than discovering it, name
  the fallback where a recipe can act as you and needs one, and stop
  rather than guessing where none is given.

**MUST NOT:**

- Write `cmd && break`, `[[ test ]] && cmd`, or a bare `VAR=$(cmd)`
  whose command may fail, inside a `set -e` recipe.
- Treat an instant exit with no output as anything other than `set -e`
  aborting before the recipe's first `echo`.
- Add a lookup for a value the caller already named. Discovery fails
  where an argument would have worked.
- Comment a recipe body except where a reader would otherwise make an
  edit that breaks it. Why it is that way belongs in the recipe under
  `docs/`.

**MAY:**

- Keep `install-hooks` unprefixed: every clone's `.envrc` and
  `post-checkout`, and every workspace built on these bricks, call it
  by that name.

## Discussion

`install-hooks` in `hooks.just` carries no `hooks-` prefix; every
clone's `.envrc` and `post-checkout` call it by that name, and so does
every workspace built on these bricks, so it keeps it.

## References

- [git-workflow](git-workflow.md) — the other conventions around
  running things in this repository.
- [ADR-0012](../../adr/0012-pre-commit-hooks.md) — the hooks
  `hooks.just` installs
