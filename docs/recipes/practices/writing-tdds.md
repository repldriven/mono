# Writing TDDs

<!-- tessl-plugin: docs -->

## Problem

You're writing a technical design under `docs/tdd/` — the engineering
contract behind a PRD, or behind a brick whose shape is decided before
it is written — and you want it in the shape every other TDD has.

## Solution

A TDD is engineering prose: it names bricks, operations, libraries,
records and routes. Nothing distils it, and an agent opens it in full,
so it carries no `tessl-plugin` label. Its sections, in order, follow.
Formatting, links and tone are [writing-docs](writing-docs.md)'s.

### Status banner

A blockquote under the title, before the first section, opening with
`**Status: proposal.**` or `**Status: implemented.**`. A proposal's
banner says what already exists and that Background names it, that
everything under Proposed Solution is the build list, and which section
says what comes first. An implemented TDD's banner is the one line.

### Objective

`## Objective` is one paragraph of what the capability is and what the
TDD decides, then two paragraphs opening with the words `In scope:`
and `Out of scope:`, each a list of what this design does and does not
decide, with a link to the document that decides each thing left out.

### Background

`## Background` is what exists — the bricks, records, patterns and
tooling the design reuses — as bullets each opening with a bold label
and naming where the thing lives. Nothing that will be built appears
here.

### Proposed Solution

`## Proposed Solution` — `## Solution` once implemented — is one `###`
per design area: the records, the brick, the component, the routes,
the security, the catcher. Each names the files it touches and the
operations, anomalies, config keys and schemas it adds. The last two
sub-sections are a first-slice section — a numbered list of what is
built first, and what follows under whose design — and `### Tests`,
one bullet per brick or base saying what its tests cover.

### Alternatives Considered

`## Alternatives Considered` is a bulleted list, each
`**The alternative.** Rejected:` and the reason in a sentence. An
alternative taken in part says which part.

### Known Limitations

`## Known Limitations` is a bulleted list, each opening with a bold
label, of what the design leaves undone or unproved.

### References

`## References` lists the PRD this design serves first, then sibling
TDDs, ADRs, recipes and external specifications, each as
`[id](path) — gloss`, the gloss saying what the document gives this
design.

## Rules

**MUST:**

- Structure a TDD as a Status banner, Objective, Background, Proposed
  Solution, Alternatives Considered, Known Limitations, References.
- Open the Status banner — a blockquote under the title — with
  **proposal** or **implemented**. A proposal's banner names what
  exists, says the Proposed Solution is the build list, and names the
  section that says what comes first; an implemented TDD's banner is
  the one line.
- Open the Objective's scope paragraphs with `In scope:` and
  `Out of scope:`, and link the document that decides each thing left
  out.
- Write Background and Known Limitations as bullets opening with a
  bold label, a Background bullet naming where the thing lives.
- Give the Proposed Solution one `###` per design area, each naming
  the files it touches and the operations, anomalies, config keys and
  schemas it adds.
- End the Proposed Solution with a first-slice section and `### Tests`,
  one bullet per brick or base saying what its tests cover.
- List each alternative as `**The alternative.** Rejected:` and the
  reason in that sentence, saying which part where one is taken in
  part.
- List the PRD the design serves first in References, then sibling
  TDDs, ADRs, recipes and external specifications, each as
  `[id](path) — gloss` saying what the document gives the design.
- Rename Proposed Solution to Solution once the design is implemented,
  and the banner with it.

**MUST NOT:**

- Label a TDD `tessl-plugin`.
- Put anything that will be built in Background, or anything that
  exists in Proposed Solution as if it did not.
- Name a consuming workspace in a library workspace's TDD. The design
  serves every workspace built on the bricks.

## Discussion

We split a TDD along the line of what exists and what will be built
because a reader arrives with one of two questions — what do I build,
or what is here — and the two sections answer one each. The Status
banner's three sentences are what let a proposal be read long after
it was written without guessing which parts landed.

The first-slice section is what turns a design into an order of work.
Each slice ends in something proved: a records slice in a migration
that runs, an API slice in scenarios that pass. Comparing the code
against the TDD once a slice lands is what shows the design drifting,
and is why the Proposed Solution names files and operations rather
than describing them.

## References

- [writing-docs](writing-docs.md) — formatting, links and tone, for
  everything under `docs/`.
- [writing-prds](writing-prds.md) — the product document a TDD serves,
  and the register that keeps the two apart.
- [smtp](../../tdd/smtp.md) — SMTP, a proposal TDD in this shape.
