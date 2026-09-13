# Git workflow

<!-- tessl-plugin: workflow -->

## Problem

You want to commit changes or push to your branch.

## Solution

Two rules sit on top of standard git practice:

1. **Merge from `main` before committing.** Renovate opens dependency
   updates against `main` on a weekly schedule, so a feature branch
   that hasn't pulled is likely already behind.
2. **Don't manually bump dependency versions Renovate manages.**
   Renovate (configured in `renovate.json`) handles the Clojure
   `deps.edn` coordinates, the GitHub Actions versions and the nix
   flake inputs. Manual bumps fight the next Renovate PR.

### Merging from main

Before committing on a branch (or on `main` directly):

```bash
git fetch origin
git merge origin/main
```

If Renovate merged a `deps.edn` change while you were working, this
pulls in the updated versions. Resolve any conflicts and re-run tests
before continuing.

If you've made local changes to a file Renovate also touched
(`deps.edn`, `.github/workflows/*`, `flake.lock`), the merge conflict
is real and needs human resolution.

### What Renovate manages

`renovate.json` configures the bot to update:

- Clojure dependencies in `deps.edn` files (workspace root, bricks,
  projects), grouped so that one PR carries a library family.
- GitHub Actions versions in `.github/workflows/*`.
- The nix flake inputs in `flake.lock`.

Renovate runs on a weekly schedule and opens PRs; a workspace may let
them auto-merge once CI passes.

### Staging deletions and moves

When `git status` shows files as `deleted:` (already gone from the
working tree), stage that with `git add -u <path>` or `git add -A`.
Don't run `git rm` — it's redundant and reads as "I'm initiating a
deletion" when in fact the user has already removed the file. If a
corresponding new path exists (e.g. `docs/slides/` after `slides/` was
deleted), it's a move and should be committed as a rename: stage both
ends with `git add` and let git's rename detection do its job.

Reach for `git rm` only when *initiating* a deletion as part of the
current task. Before staging deletions, check whether the missing files
are (a) the user's intentional moves or removals, already done in the
working tree, or (b) something you're starting yourself. The framing
matters: `git rm` reads as a fresh destructive op; `git add` reads as
recording the user's existing changes.

### Working with untracked drafts

The user may have untracked work-in-progress in the working tree —
draft projects, draft components, local plans. Two rules cover
collaboration with those drafts:

- **Don't develop in the user's drafts.** No new features, no scope
  creep into their WIP. Drafts are the user's active line; advancing
  them beyond what they've staged is out of scope.
- **Do maintain consistency through workspace operations.** Renames,
  dead-code cleanup, schema regenerations, and similar workspace-wide
  ops apply to draft files too. Refusing to touch drafts and asking the
  user to remember every place to follow up leaves the workspace
  inconsistent; they won't remember and the next operation breaks.

The line is between "keeping drafts consistent" (do) and "advancing
drafts beyond what they've staged" (don't). When unsure, do the
maintenance and report what was touched in drafts in the summary so the
user can spot-check.

### When you genuinely need a version bump

If you need a dependency version Renovate hasn't yet picked up (security
fix, runtime bug fix, a transitive incompatibility you've just
discovered):

1. Check whether a Renovate PR is already open for it.
2. If you must bump manually, keep the commit narrowly scoped so the
   next Renovate PR can either close (if you've already landed the same
   bump) or merge cleanly (if Renovate goes higher).

## Rules

**MUST:**

- Pull/merge from `main` before committing.
- Resolve conflicts with Renovate-managed files (`deps.edn`,
  `.github/workflows/*`, `flake.lock`) before pushing.
- Stage user-initiated deletions and moves with `git add` — not
  `git rm`.

**MUST NOT:**

- Manually bump dependency versions Renovate manages, except when a
  real need requires a version Renovate hasn't yet caught up to.
- Develop new features inside the user's untracked drafts.

**SHOULD:**

- Include the user's untracked drafts in workspace-wide operations
  (rename, dead-code cleanup, regeneration) so the tree stays
  consistent. Report what was touched in drafts.

## Discussion

Renovate exists so dependency upkeep doesn't fall on humans. The
trade-off is that branches go out of date faster — a week of feature
work can be sitting behind several merged Renovate PRs. Pulling `main`
before committing keeps the gap small and catches conflicts early.

The "don't manually bump" rule is about avoiding two PRs fighting for
the same `deps.edn` line. Renovate's PR will conflict with a manual
bump and either auto-close or fail to merge; either way it's churn. Let
Renovate do its job.

If a build genuinely needs a newer version than Renovate has picked up,
bumping manually is fine — just keep the commit narrow so the bot's
next PR can reconcile cleanly.

## References

- `renovate.json` (Renovate configuration)
- [Renovate documentation](https://docs.renovatebot.com/)
- [justfile-recipes](justfile-recipes.md) — the other conventions
  around running things in this repository
