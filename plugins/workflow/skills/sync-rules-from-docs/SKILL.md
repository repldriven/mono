---
name: sync-rules-from-docs
description: |
  Regenerate a Tessl rule file's section bodies from the
  recipes/ADRs it links to, so the rule stays traceable to its
  source instead of drifting as an independently-paraphrased copy.
  Also discovers recipes/ADRs labeled for a plugin
  (`<!-- tessl-plugin: <name> -->`) that have no rule section yet, and
  flags linked docs whose label has drifted to a different plugin.
  Triggers on: "sync the idioms rule", "regenerate rules from docs",
  "check the rule against the docs", "did the recipe change break
  the rule", "does idioms cover everything it should", "find missing
  rule sections". Defaults to plugins/idioms/rules/idioms.md; accepts
  another rule file's path.
license: Apache-2.0
allowed-tools: Bash Read Edit
metadata:
  version: '0.1'
  author: kjothen
  domain: software-engineering
  subdomain: docs-tooling
  tags: 'tessl, rules, docs, recipes, adr, generation'
---

# sync-rules-from-docs

**Read the docs, write the rule** — never the other way around. Compose
each rule section from the extracted material only. Where a rule says
something that traces to no extracted bullet, surface it as a finding
rather than preserving it.

## When to Use

After editing a recipe's `## Rules` block or an ADR's `## Decision`
section, or when asked to "sync the idioms rule" / "check the rule
against the docs". Also useful as a periodic sanity check even when
nothing's knowingly changed — it's cheap and it catches silent drift.

**Do not use** for authoring a rule section from a doc that isn't
labeled for this plugin and isn't yet linked (there's no traceable
basis — label the doc first, or write the recipe/ADR from scratch),
or for any docs outside `docs/recipes/` and `docs/adr/` (the extraction
shapes below are specific to those two).

## Workflow

1. **Discover**: `bash plugins/workflow/skills/sync-rules-from-docs/discover.sh <plugin-name> [rule-file]`
   (plugin name derived from the rule file's own plugin, e.g. `idioms`
   for `plugins/idioms/rules/idioms.md` — the default rule-file path is
   `plugins/<plugin-name>/rules/<plugin-name>.md`). Also
   deterministic and judgment-free: it scans every `docs/recipes/*/*.md`
   and `docs/adr/*.md` for a `<!-- tessl-plugin: <name> -->` label on
   the front matter, between the title and the first section, and
   cross-references against the rule
   file's existing `See [...]` links. Reports two finding types:
   - **`unlinked: <doc>`** — labeled for this plugin, no rule section
     references it yet. This is now a well-founded case for authoring
     a *new* section — the doc has both real `## Rules` /
     `## Decision` content and an explicit label, so treat it exactly
     like an existing section for steps 2–4 and 6 below: extract,
     compose, leave nothing untraceable, write. Step 5 doesn't apply —
     there's no existing heading or `See [...]` line to leave
     untouched, so write a brand-new heading and `See [...]` line as
     part of step 6 instead.
   - **`mismatched: <doc>`** — currently linked from a rule section,
     but labeled for a *different* plugin (or not labeled at all).
     Report it; don't act on it.

2. **Run the extractor**: `bash plugins/workflow/skills/sync-rules-from-docs/extract.sh [rule-file]`
   (defaults to `plugins/idioms/rules/idioms.md`). This is a
   deterministic, judgment-free pass — it locates each rule section's
   trailing `See [...]` line, resolves every linked doc, and prints:
   - For a **recipe**: its `## Rules` section's `**MUST:**` /
     `**MUST NOT:**` bullets verbatim (and `**SHOULD:**` if present).
   - For an **ADR**: its `## Decision` section's lead paragraph, plus
     a colon-ending intro line and the list immediately following it,
     if that shape is present.

   If the extractor reports `ERROR: <doc> has no ## Rules section` (or
   `## Decision`), **stop for that section**: fix the doc's structure,
   re-run `extract.sh`, and continue once that section extracts
   cleanly. Don't guess at what the rule should say.

3. **For each section, compose the rule body from the extracted
   material only.** This is the one step that needs judgment — a
   readable rule can't be a raw bullet dump — but every factual claim
   in the composed prose (a function name, a forbidden call, a file
   name) must trace to something the extractor printed for that
   section. Two things the raw extraction doesn't resolve for you:
   - **ADR lists are ambiguous by shape.** "The rules: 1. …" (normative
     — belongs in the rule) and "Worked examples: - …" (illustrative —
     usually belongs left in the doc, not restated) look identical to
     the extractor. Read the extracted list and judge which kind it is;
     when a recipe is *also* linked for the same section, prefer its
     MUST/MUST NOT as the backbone and treat the ADR material as
     supporting color, not a second thing to restate in full.
   - **Compression, not concatenation.** Multiple MUST/MUST-NOT bullets
     from one or two docs should read as the rule's existing voice —
     short, imperative, "how to write" not "what's forbidden" — not as
     a pasted list. Match the surrounding sections' tone and length.
   - **Carry the `Commands:` line verbatim**, immediately above the
     `See [...]` line, wherever the extractor emits one. It lists every
     `just` recipe the linked docs' Rules name, in first-appearance
     order. Keep the command names out of the section's prose: the
     names are what an agent needs ambiently, and how to read their
     output belongs in the recipe behind the `See` link.

4. **Flag untraceable claims.** Before rewriting a section, diff its
   *current* body against the extracted material. If the current text
   asserts something with no matching bullet or Decision lead anywhere
   in the extraction, call it out explicitly in the report — don't
   silently drop it (it might be correct but the doc is missing it)
   and don't silently keep it (it might be stale). This is a decision
   for whoever reviews the diff, not for this skill to make
   unilaterally.

5. **Leave the section heading and the trailing `See [...]` line
   untouched.** They're already the pointer to source; only the body
   paragraph between them gets rewritten. This step doesn't apply to a
   brand-new section authored from an `unlinked` finding — there's no
   existing heading or `See [...]` line yet, so step 6 writes both for
   the first time instead of preserving them.

6. **Write the file.** No interactive confirmation gate — this
   produces a normal git diff for review via the usual PR flow.
   Don't run `git commit` yourself; that's a separate, explicit step.

7. **Reinstall the plugins from local files** so the edited rule
   reaches the copy agents actually load:

   ```bash
   just tessl-plugins-install
   ```

   `just tessl-plugins-check` reports whether the installed rules match
   their sources, and is what catches a sync that stopped here.

   `plugins/<name>/rules/<name>.md` is the tracked source, but
   `AGENTS.md` loads `.tessl/RULES.md`, which points at
   `.tessl/plugins/<workspace>/<name>/rules/<name>.md` — a gitignored,
   per-machine copy. Editing the source alone leaves every agent
   reading the pre-sync rule. The `file:` refs install from the
   working tree rather than the registry, so this needs no network and
   no login. It touches only gitignored paths, so it never shows up in
   the diff from step 6.

## Output

Start with the discovery findings, then the per-section sync report.

**Discovery**: list every `unlinked` and `mismatched` finding from
step 1. For each `unlinked` finding that became a new section, say so
inline; for each `mismatched` finding, note that it was reported only
— no file changed as a result.

**Per section**: which doc(s) it extracted from, a short summary of
what changed (or "unchanged" if the compressed prose already matched,
or "new section" for one authored from an `unlinked` finding), and any
untraceable claims found. If a section's source doc had a structural
`ERROR`, report that and leave the section as-is.

**Reinstall**: confirm the plugins were reinstalled from local files,
so the reader knows the edited rule is live rather than pending.

Example:

```
Discovery
  unlinked: docs/recipes/code/system-configurations.md (labeled 'idioms', no rule section links it) -> authored new section below
  mismatched: none

Return anomalies, don't throw across a boundary
  Sources: docs/recipes/code/error-handling.md, ADR-0005
  Changed: added "anomaly category names the call site" (was missing)
  Untraceable: none

Comment the why, not the what
  Sources: ADR-0015
  Changed: unchanged
  Untraceable: none

YAML system configs stay declarative
  Sources: docs/recipes/code/system-configurations.md
  Changed: new section
  Untraceable: none
```
