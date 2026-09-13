---
name: check-docs
description: |
  Run every doc-quality check from the writing-docs recipe — wrap at
  80, mermaid semicolons, link hygiene, maturity claims, competitor
  names, brittle counts, and the PRD register where a workspace has
  PRDs — across docs/, readme.md and CLAUDE.md, and report each
  finding against the rule it breaks. Triggers on: "check the docs",
  "run check-docs", "is this doc wrapped", "lint the markdown", "did I
  break a link".
license: Apache-2.0
allowed-tools: Bash
metadata:
  version: '0.1'
  author: kjothen
  domain: software-engineering
  subdomain: docs-tooling
  tags: 'docs, markdown, lint, recipes, adr'
---

# check-docs

The recipe is the source of truth for *what's wrong* and *how to fix
it*. This skill is the runner: it runs every verification command from
`docs/recipes/practices/writing-docs.md` across the workspace's
markdown and reports findings.

## Scope

- Every `*.md` under `docs/`, except `docs/slides/` (slidev
  presentations) and `docs/plan/` (in-flight plans, often containing
  quoted REPL output).
- `readme.md` and `CLAUDE.md` at the repo root, where they exist.
- The `writing-*.md` recipes under `docs/recipes/practices/` are
  excluded from content-pattern checks (paren-adjacent links,
  code-as-link-text, maturity overclaim, competitor names,
  brittle-temporal) because they document those patterns by design
  with "Bad" / "OK" examples. They are still subject to wrap and
  mermaid checks.
- The competitor names refused are the workspace's own, one per line in
  `.config/check-docs/names`; without that file the check passes. The
  PRD checks run only where `docs/prd/` holds files.

## Workflow

The script sits beside this file. `<skill-dir>` below is the skill's
base directory, reported when the skill loads — the installed copy under
`.tessl/plugins/mono/docs/skills/check-docs`.

1. Run `bash <skill-dir>/checks.sh` from the repository root.
2. Treat every section that says `PASS` as a clean check.
3. For each `FAIL` section, map the file:line reference to the relevant
   rule in `docs/recipes/practices/writing-docs.md`, or in
   `writing-prds.md` beside it for a PRD finding. The rules and their
   rationales live there; don't restate them, just follow them.
   - Suggest a fix that follows the recipe's "OK" pattern, not its
     "Bad" pattern.
   - For inline-code-as-link-text findings, the standard exception is a
     library citation such as `` [`de.otto/nom`](url) ``. These are
     conventional in existing recipes and ADRs and are likely
     intentional; flag them but don't suggest changing them unless the
     user asks.
   - For wrap findings, check whether the long line is inside a
     markdown table (rows can't easily wrap) or an image link with a
     long URL — those are usually acceptable. Otherwise, rewrap to 80.
   - For paren-adjacent-link findings, restructure with an em-dash or
     comma so no `)` sits adjacent to a link's `)`.
   - For brittle-temporal findings, replace counts, datestamps and
     "recently" with timeless framings ("each", "the relevant", drop
     the count).
   - For PRD findings, prefer the product register `writing-prds.md`
     lists over the engineering register.
4. Report a summary: one line per check (`wrap: 0`,
   `mermaid-semicolon: 0`, `paren-adjacent: 1 in tdd/idempotency.md`,
   …), then for each non-empty check the file:line refs and a suggested
   fix per item. If everything passes, say so in one line and stop.

## Editing the script

The checks live in `checks.sh` beside this file, in mono's `docs`
plugin. Edit it there: add a check, tighten a regex, or adjust the file
scope, keeping new checks structured the same way (`section` +
`report` calls) so the output format stays consistent. A workspace that
imports the plugin gets the change at its next bump.
