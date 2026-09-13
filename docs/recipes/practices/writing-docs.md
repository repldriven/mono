# Writing docs

<!-- tessl-plugin: docs -->

## Problem

You're writing or editing a document under `docs/` — an ADR, a recipe,
or whatever else a workspace keeps there — and you want it to fit the
conventions for formatting, tone, link hygiene, and mermaid diagrams.

## Solution

Treat `docs/` as a single readable surface. A few hard rules are driven
by how the docs are read (Neovim with a column ruler at 80, GitHub
markdown, mermaid rendering), plus a tone register that's deliberately
understated. What each kind of document is made of is its own recipe:
[writing-recipes](writing-recipes.md), [writing-adrs](writing-adrs.md),
[writing-tdds](writing-tdds.md) and [writing-prds](writing-prds.md).

### Hard-wrap at 80 columns

All Markdown under `docs/` hard-wraps at 80 columns. Wrap prose
paragraphs and indent bullet continuation lines by 2 spaces.

```
- A bullet whose prose runs long enough to wrap should
  continue with a 2-space indent on subsequent lines.
```

What cannot wrap is not measured: a fenced block, a table row, an
HTML tag, and a link's target. A line is measured with its
`](...)` targets removed, so a long URL never forces a break in
the link around it, while the prose beside it still has to fit.

Verify with the `check-docs` skill, which the `docs` plugin installs as
`tessl__check-docs`; its wrap check applies those exemptions. The bare
form, for one file with none of them:

```
awk '{ if (length($0) > 80) print FILENAME":"NR": "length($0) }' <file>
```

The 80-column rule exists because the docs are read in Neovim
with a colorcolumn ruler at 80; long lines render with the
ruler overlapping content. Workspace Clojure code is already
80-wrapped via zprint; docs match.

### The tessl-plugin label

A recipe or ADR distilled into a plugin rule carries
`<!-- tessl-plugin: <name> -->` in its front matter — after the title,
before the first section. Anywhere in that window is found; the exact
line is not load-bearing.

A markdown formatter puts a blank
line after a heading, so a label pinned to line 2 moves to line 3 the
first time somebody saves the file, and a parser reading a fixed line
then reports the doc as unlabelled — indistinguishable from one nobody
has labelled, and silent. Documents should be free to be formatted, so
the tooling tolerates the shapes formatting produces.

Which means: do not move a label to satisfy a script. If discovery
cannot find a labelled doc, the script is wrong.

### Markdown link hygiene

Three patterns break the project's markdown viewers and should
be avoided.

**1. Don't put `)` immediately after a link's closing paren.**

```
;; Bad — adjacent `))` confuses the viewer
The pattern (see [foo.md](foo.md)) exists.

;; OK — em-dashes or comma split avoids the adjacency
The pattern — described in [foo.md](foo.md) — exists.
```

A `.` or other punctuation after a link is fine. Only an
adjacent `)` is the problem.

**2. Don't wrap link text across lines.**

```
;; Bad — link text spans two lines
- [ADR-0001 — Reuse mono as
  upstream](../../adr/0001-reuse-mono-as-upstream.md)
```

Use the canonical reference-list pattern: link first, em-dash,
title as plain prose. The link stays intact; the trailing
prose can wrap.

```
;; OK — link intact, title fits inline
- [ADR-0001](../../adr/0001-reuse-mono-as-upstream.md) — Reuse mono as upstream

;; OK — link intact, title wraps because the path is long
- [ADR-0011](../../adr/0011-one-component-per-third-party-library.md) —
  One component per third-party library
```

**3. Don't use inline code as the entire link text.**

```
;; Bad — backticked path as link text trips some parsers
[`slides/foo/bar.md`](../slides/foo/bar.md)

;; OK — plain link text
[slides/foo/bar.md](../slides/foo/bar.md)
```

**4. Climb at most two levels (`../../`), and never three.**
Recipes sit one level deeper than everything else under `docs/`
— `docs/recipes/<chapter>/` — so a recipe reaching an ADR is
`../../adr/...` and there is no shallower spelling of it. A
recipe reaching another chapter is `../<chapter>/...`, and
anything outside `docs/` reaching in is `../recipes/<chapter>/...`.
Three or more means the doc is reaching for a file that is not
prose, which is item 5 rather than a longer climb.

**5. Reach a non-markdown file with a repo-root link, never a
climb.** A doc naming a file it does not live beside — a chart's
values, a composition, a roles table, a hook script — links it
from the repository root:

```
;; Bad — a climb out of docs/, and dead in a local editor
[organisation-roles.json](../../../infra/access/organisation-roles.json)

;; Bad — a path the reader has to go and find
The roles are declared in `infra/access/organisation-roles.json`.

;; OK — repo-root, one spelling from anywhere under docs/
The roles are declared in
[organisation-roles.json](/infra/access/organisation-roles.json).
```

GitHub resolves a leading `/` against the repository root rather
than the site root, so one spelling works from any depth and
never needs adjusting when a doc moves. Markdown is the
exception and stays relative: `docs/` is a self-contained tree
that is navigated rather than pointed at, and a relative link
between two docs works in every editor as well as on GitHub.

Two things stay in backticks. A **generic filename** — "a
project's `deps.edn`", "the brick's `system/core.clj`" — names a
shape rather than one file, and linking it sends the reader to
an arbitrary instance of it. A **path carrying a placeholder** —
`infra/platform/crossplane-xrds/<parent>-composition.yml` — has
no file to resolve to. Link the path where it names one real
file, and only then.

Link text is the file's name, not the whole path, and not the
name in backticks — item 3 applies here as everywhere. A deep
path as link text overruns 80 columns, and a link is the one
thing that cannot be wrapped to fit. Where the prose needs to
say which of two files it means, it says so around the link.

### Mermaid diagrams

**Don't use `;` inside mermaid labels, notes, or arrow text.**
Mermaid treats `;` as a statement separator; using it inside
human-readable strings splits the line at the semicolon and the
parser then sees a malformed continuation. GitHub fails to
render the block.

```
;; Bad
  Note over A,B: first thing; second thing

;; OK — comma, em-dash, period, or <br/> instead
  Note over A,B: first thing, second thing
  Note over A,B: first thing — second thing
  Note over A,B: first thing.<br/>second thing.
```

Don't try to escape the semicolon (`\;`, HTML entity); mermaid
doesn't honour either. The same pitfall applies across mermaid
grammars (sequence, flowchart, state).

**Mermaid lines can exceed 80 chars when needed.** The 80-col
wrap is for prose. A mermaid sequence label, node label, or
arrow text that reads more clearly as a single line at, say,
90 chars should stay one line — don't insert `<br/>` purely to
hit 80. Use `<br/>` only when the label *should* render on two
lines in the diagram.

### Write plainly

Give the reader the facts and the instructions, in the fewest words
that stay precise. Six constructions to cut on sight.

**Merit.** Nothing earns, deserves, or is worth its place. Say what
the thing is or does.

```
;; Bad
A failure earns its entry by misleading.
Brevity alone does not earn it.
That is the seam worth knowing about.

;; OK
A failure gets an entry when its message misleads.
Brevity alone is not a reason.
That is where the work stops being performed and starts being
recorded.
```

**An objection nobody made.** "Not arbitrary", "deliberate rather
than lax", "never a step done differently" — each raises a doubt in
order to answer it, and the reader did not have the doubt.

```
;; Bad
The order is not arbitrary and the dependencies run one way.

;; OK
The dependencies run one way.
```

**The closing aphorism.** A last sentence that generalises what was
just said and carries no fact: "a second invites a third, and a
document where four things are highlighted highlights nothing."
Delete it.

**A second phrasing.** Restating a point in other words reads as
emphasis and lands as padding. Say it once.

**A gesture where a name exists.** "What pays for it" is a billing
account. "Where its manifests live" is a repository. Name the thing,
and stop the sentence where the naming stops.

```
;; Bad
who holds which capability, which folder the installation is, what
pays for it, and where its manifests live

the seed identity that creates folders and projects on behalf of all
of it

;; OK
the installation's folder, its manifests repository, its billing
account, and a principal for each capability

the seed identity that creates folders and projects
```

**An epigram where a label belongs.** A `###` heading and a bold
paragraph label are index entries. Name the thing in the words
somebody would search for.

```
;; Bad
**What is not yet true, and should not be assumed.**
**The traps, all of one family**

;; OK
**Known limitations.**
**Known problems.**
```

A Failures entry is the exception. It is keyed on what the reader is
looking at, so its label is an observation — "a repository reported
unreachable" — rather than a category.

Do not narrate the writing. A recipe describes the system as it
stands — never what the page used to say, never which recipes it
replaces, never that a section reads as something it is not. That is
what `## Status` already says of itself, applied to the whole page: it
belongs in the commit that changed it.

### Tone

Two rules keep the docs honest and durable.

**No maturity overclaim.** Don't describe these bricks, a workspace
built on them, or any of their components as "battle-tested",
"production-proven", "years of hardening", or similar
maturity-framing. They are new; they have tests and they work, but
they don't have production miles. Stick to
literal facts: "exists", "has tests", "is the only place this
code lives", "designed to be reusable from the start". Reuse
arguments rest on avoiding duplication and architectural
cleanness, not on imaginary track record.

**No competitor names.** Don't name a specific company in the
workspace's own space — a bank, a fintech, a vendor it competes with —
in any doc. Use generic phrasing instead. The names the `check-docs`
skill refuses are the workspace's own, one per line in
`.config/check-docs/names`.

```
;; Not OK
Daily-compounded interest, which Acme Bank and Bravo Bank both
offer today.

;; OK
Daily-compounded interest, which an increasing number of
digital banks offer to compete on rate visibility.
```

If a real reference is genuinely useful, cite a public spec,
RFC, or standard (ISO 20022, FPS scheme rules) rather than a
vendor.

### No specific counts, dates, or "recent" framings

Don't pin a doc to a snapshot of repo state — the snapshot
ages worse than the prose around it.

```
;; Not OK
- `docs/adr/` — fourteen records covering ...
- `docs/recipes/` — thirteen recipes covering ...
- The thirteen service projects under projects/ ...

;; Also not OK
This was added a fortnight ago.
As of May 2026, the project has 47 bricks.

;; OK
- `docs/adr/` — architecture decisions, one per load-bearing
  choice.
- `docs/recipes/` — task-oriented recipes; read the relevant
  one before doing any non-trivial task.
- Each service project under projects/ ...
```

The rule applies to:

- **Counts of artefacts** — "fourteen ADRs", "thirteen
  recipes", "twenty-one bank bricks". Use plurals or
  "each" / "the relevant" / "every" instead.
- **Datestamps** — "as of May 2026", "added in Q2".
  References to commits, PRs, or release tags age the
  same way; describe the *state* the doc captures, not
  when it was captured.
- **"Recently" / "recently added" / "lately"** — relative
  time anchors that no longer mean what they meant when
  written.

If a count or date is genuinely load-bearing (e.g. a regulator
threshold, a fixed-cardinality enum), it can stay — that's not
project state, it's external. Otherwise, drop it.

### Aspiration over enforcement

When a doc captures a code-quality rule (one-component-per-
library, no-bang naming, helper-convergence in `utility`, and
so on), frame it as a **principle and discipline** rather than a
mechanical CI-enforced gate. Acknowledge that drift happens
during ordinary development and the practice is to catch it
during code review or periodic audit.

Soften absolute language ("exactly", "must", "violation") in
favour of "the principle is X / reality is messier" framing.
Mention any audit mechanism (`clj -M:poly libs`, etc.) as
*visibility*, not enforcement. Acknowledge drift in the Harder
consequences section explicitly. Don't pitch CI gates unless
the rule is genuinely mechanical and the user has asked for one.

This sits in tension with the MUST / MUST NOT / SHOULD list at
the foot of every recipe — that list is shorthand, not a
contract, and the body text gives the framing.

## Rules

**MUST:**

- Hard-wrap markdown at 80 columns under `docs/`.
- Write in the fewest words that stay precise — the facts and the
  instructions, and nothing else.
- Use the canonical reference-list pattern for ADR / recipe / TDD /
  PRD links: `[ID](path) — Title`.
- Keep relative links inside `docs/` to two levels at most,
  which is what a recipe reaching an ADR costs.
- Link a non-markdown file from the repository root, with the file's
  name as the link text — `[values.yaml](/infra/helm/…/values.yaml)` —
  where the path names one real file. GitHub resolves a leading `/`
  against the repository root, so one spelling works from any depth,
  and a whole path as link text would not fit in 80 columns.
- Replace `;` inside mermaid labels, notes, and arrow text
  with `,`, `—`, `.`, or `<br/>`.
- Carry `<!-- tessl-plugin: <name> -->` after the title of a recipe or
  ADR a plugin rule distils, and in no TDD or PRD.

**MUST NOT:**

- State the same fact under two headings.
- Say that anything earns, deserves, or is worth its place.
- Raise an objection nobody made in order to answer it — "not
  arbitrary", "deliberate rather than lax".
- Close a passage with a sentence that generalises it and carries no
  fact.
- Say the same thing twice in other words.
- Gesture at a thing that has a name — "what pays for it" for a
  billing account, "where its manifests live" for a repository.
- Title a section or a bold paragraph label with an epigram. Name it
  in the words somebody would search for — "Known limitations", not
  "What is not yet true, and should not be assumed".
- Narrate the writing: what the page used to say, which recipes it
  replaces, or how a section reads now.
- Put `)` immediately after a link's closing paren.
- Wrap link text across lines.
- Use inline code as an entire link text.
- Use relative links that climb three levels or more
  (`../../../`).
- Use a repo-root link for a markdown file. Between docs the link
  is relative, which works in an editor as well as on GitHub.
- Link a generic filename (`deps.edn`) or a path carrying a
  placeholder. Both stay in backticks: one names a shape rather
  than a file, the other resolves to nothing.
- Describe these bricks, a workspace built on them, or their
  components as "battle-tested", "production-proven", or similar
  maturity claims.
- Name a specific competitor in any doc. The names the checker
  refuses are the workspace's `.config/check-docs/names`.
- Pin docs to specific counts of repo artefacts (number of
  ADRs, recipes, services, bricks) or to relative-time
  framings ("recently", "as of …", "a fortnight ago").
- Move a `tessl-plugin` label to satisfy a script.

**SHOULD:**

- Frame code-quality rules as principle and discipline, not
  mechanical CI gates. Acknowledge drift in the Harder
  consequences.
- Let mermaid lines exceed 80 chars when the diagram reads
  more clearly as a single line.
- Cite public specs / RFCs / standards rather than vendors
  when an industry reference is useful.

## Discussion

Most of these rules exist because the docs are read in
specific tools, not generic markdown viewers. Neovim's
colorcolumn drives the 80-col rule; the project's markdown
viewer drives the link-pitfalls; GitHub's mermaid renderer
drives the no-semicolon rule. They're surface-quality rules,
but ignoring them produces visibly broken pages, and the
fixes are easy to land at write time.

The plain-prose rules are a list of constructions rather than an
instruction to be concise, because concision is not what goes wrong.
Ornament is: a sentence that sounds like a conclusion, in a place
where there was nothing left to say.

The tone rules — no maturity overclaim, no competitor names —
exist because the docs may end up public (in the GitHub repo,
on a docs site, in a published OpenAPI). Naming a competitor,
even accurately, ages badly as their product changes; claiming
maturity that isn't there reads as marketing rather than
engineering. Stick to what's literally true and the docs age
gracefully.

The aspiration-over-enforcement framing matters because the
project's quality rules are not always enforceable in CI. A
naming convention drift, a missed brick boundary, a helper
that ended up in two places — these are caught in review or
periodic audit, not by a green/red check. Pretending the
rules are mechanical when they aren't sets the wrong
expectation; framing them as principle and discipline matches
the lived reality and keeps the rule from going stale when the
codebase doesn't perfectly comply.

## References

- [writing-recipes](writing-recipes.md) — what a recipe is made of.
- [writing-adrs](writing-adrs.md) — what an ADR is made of.
- [writing-tdds](writing-tdds.md) — what a TDD is made of.
- [writing-prds](writing-prds.md) — what a PRD is made of, and its
  register.
- [ADR-0015](../../adr/0015-comments-and-docstrings.md) — Comments and
  docstrings
- [code-style.md](../code/code-style.md)
- [GitHub flavoured markdown](https://github.github.com/gfm/)
- [Mermaid documentation](https://mermaid.js.org/)
