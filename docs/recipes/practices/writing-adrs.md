# Writing ADRs

<!-- tessl-plugin: docs -->

## Problem

You're recording an architecture decision under `docs/adr/` — a
load-bearing choice the workspace made, and what it was chosen over —
and you want it in the shape every other ADR has, so the docs plugin
can distil its Decision into a rule.

## Solution

An ADR is four sections after its title and label, in this order.
Formatting, links and tone are [writing-docs](writing-docs.md)'s.

### Title and number

The file is `NNNN-slug.md`, four digits, the next free number. A
number is never reused, so the sequence may have gaps. The title is
`# N. Title`, the number without leading zeros, and the title names the
decision — "One component per third-party library" — rather than the
topic. The `<!-- tessl-plugin: <name> -->` label follows the title.

### Status

`## Status` is one word as its own paragraph: **Proposed**,
**Accepted**, or **Superseded by** and a link to the ADR that replaced
it. A superseded ADR is not edited beyond that line: it records what
was decided then, and the successor records what is decided now.

### Context

`## Context` says what forced a decision: the property wanted, what
happens without one, and the shortlist of ways to get it. The
shortlist is a bulleted list, each `**The option.**` in bold and a
sentence or two, a rejected option saying why in that sentence. The
chosen option is last, and the Decision says it in full.

### Decision

`## Decision` opens with the decision in a paragraph a reader could
stop at. Where the decision has parts, a line ending in a colon
introduces a list of them, normative and in the imperative. Worked
examples from the workspace follow the rule, introduced as examples so
a reader can tell them from it. A long Decision carries `###`
sub-sections, one per aspect, when the parts have their own rationale.

### Consequences

`## Consequences` is two lists, `Easier:` and `Harder:`, each a label
on its own line followed by bullets. Easier says what the decision
makes simple and names the worked example where one exists. Harder
says what it costs, what needs judgement per case, and where drift
happens: a rule that is a discipline rather than a gate says so here,
and names the audit that makes drift visible.

## Rules

**MUST:**

- Name an ADR `NNNN-slug.md` with the next free number, and title it
  `# N. Title` with the decision as the title.
- Structure an ADR as Status, Context, Decision, Consequences.
- Open the Decision with the decision in one paragraph, and introduce
  its parts, where it has parts, with a line ending in a colon and a
  list.
- Write the Consequences as `Easier:` and `Harder:` lists, and
  acknowledge drift under Harder.
- Mark a replaced ADR **Superseded by** its successor, and leave the
  rest of it as it was.

**MUST NOT:**

- Reuse an ADR number.
- Edit a superseded ADR's Context, Decision or Consequences.
- Present a worked example as the rule.

## Discussion

We shaped an ADR so that one section carries the normative content and
the rest carries the reasoning around it, because the docs plugin
reads only the Decision. The `sync-rules-from-docs` skill extracts a
Decision's lead paragraph and, where present, the colon-introduced
list that follows it, so a decision stated only in Context or only in
a Consequences bullet never reaches an agent. Worked examples sit
after the rule for the same reason: the extractor cannot tell a rule
list from an example list by shape, and the person composing the rule
has to.

An ADR is a record. A decision that no longer holds is superseded by a
new number rather than rewritten, so the history of why the workspace
looks as it does stays readable in order.

## References

- [writing-docs](writing-docs.md) — formatting, links, tone, and the
  label, for everything under `docs/`.
- [ADR-0011](../../adr/0011-one-component-per-third-party-library.md) —
  One component per third-party library, an ADR with the shortlist in
  its Context and both Consequences lists.
- [ADR-0015](../../adr/0015-comments-and-docstrings.md) — Comments and
  docstrings, an ADR whose Decision carries sub-sections.
