# Writing PRDs

<!-- tessl-plugin: docs -->

## Problem

You're writing or editing a product requirements document under
`docs/prd/`, and you want it readable by the people it is for and in
the shape every other PRD has.

## Solution

PRDs are read by product-shaped readers — product managers, designers,
compliance, executives — not engineers. The shape, and the rules on
register that follow from the readers, are below, on top of
[writing-docs](writing-docs.md), which covers everything under `docs/`.

### What a PRD is made of

The sections, in this order:

- `## Objective` — what the capability is and what this PRD covers, in
  a few paragraphs, with the line between it and its neighbours.
- `## Users and stakeholders` — who reads it, one bold paragraph label
  per persona, each saying what that person does and cares about. A
  workspace with a platform PRD names its personas there, and every
  other PRD takes its readers from that list, so the same word means
  the same person everywhere. A counterparty the platform integrates
  with is a stakeholder where it appears, never a persona. In a
  library workspace the one persona is the developer building on its
  bricks.
- `## Goals` — what the capability delivers, as bold-labelled bullets.
- `## Non-goals` — what it deliberately does not, in the same shape,
  each naming where the thing lives if it lives somewhere.
- `## Functional scope` — one `###` per part of the capability, in
  product language, saying what a user does and what the platform does
  in return.
- `## User journeys` — numbered `###` sections, each a mermaid sequence
  diagram of user-visible beats — a person, the app, the platform, a
  counterparty — and a paragraph reading it.
- `## Open questions` — bold-labelled bullets of what is not decided,
  each saying what is assumed meanwhile.
- `## References` — the neighbouring PRDs with what each holds, and the
  TDD that serves this one.

A PRD carries no `tessl-plugin` label: nothing distils it, and an
agent opens it in full.

### Use non-technical product language

Engineering vocabulary doesn't belong in a PRD even when it's accurate.

- Avoid: synchronous, asynchronous, reactive, primitive, watcher,
  relay, handler, idempotent, deterministic, transaction (in the
  engineering sense), event, dispatch, subscribe, poll (as a verb of
  art), choreography, saga, orchestrator.
- Prefer: "in the background", "automatically", "without the customer
  having to do anything", "once X completes", "the platform offers a
  way to", "a means of comparing".
- "Atomic" is borderline — OK if framed as "all-or-nothing" with a
  quick gloss; better to say "either the account comes up complete or
  doesn't come up at all".
- Internal-mechanism words (changelog, the database, message bus,
  brick) never belong in a PRD.
- Sequence diagrams in PRDs describe user-visible beats, not internal
  hops between components.

```
;; Not OK — engineering register
The IDV flow is asynchronous and reactive. A name-matching
primitive lets callers compare names. A relay publishes an
event when the changelog updates.

;; OK — product register
Identity verification runs in the background; the customer
doesn't wait for it. The platform offers a way to compare two
names softly. Activation happens automatically once
verification completes.
```

### Describe what users do, not the operation name

PRDs say "uses the API to X" — they don't name specific operations
like `create-organization` or `submit-payment`. Operation names belong
in TDDs and the OpenAPI spec.

```
;; Not OK
The platform exposes a `create-organization` operation.
The admin calls `create-organization` with name, type, ...

;; OK
A platform admin uses the API to create a new organisation in a
single call. The call accepts the organisation's name, type, ...
```

Inputs and outputs to a call can still be listed, framed as "the call
accepts" / "the call returns" — that's user-relevant without naming the
operation.

### Name the capability, not a consumer

In a library workspace — one whose bricks other workspaces build on —
a PRD describes the capability a workspace built on those bricks gets,
and names no consuming workspace: the need is stated as what every such
workspace gets, not as what one of them asked for. Its reader is the
developer building on the bricks, so the developer is its one persona
and every user journey is the developer's: the people a platform's mail
reaches, and whoever operates an installation, are the developer's
users and appear through the developer. A vendor an installation might
use is a worked example, not a persona. A workspace that is itself the
product names its own personas and its own installation as it likes.

### The names the checker refuses

The companies no doc may name are listed one per line in
`.config/check-docs/names`, which the `check-docs` skill reads. Add a
name there when one turns up in a draft.

## Rules

**MUST:**

- Structure a PRD as Objective, Users and stakeholders, Goals,
  Non-goals, Functional scope, User journeys, Open questions,
  References.
- Say in the Objective where the line between this PRD and its
  neighbours falls.
- Take a PRD's personas from the workspace's platform PRD where one
  exists, and describe each as what they do and care about.
- Write Goals, Non-goals and Open questions as bold-labelled bullets, a
  non-goal naming where the thing lives if it lives somewhere and an
  open question saying what is assumed meanwhile.
- Draw a user journey as a numbered `###` section holding a mermaid
  sequence diagram of user-visible beats and a paragraph reading it.
- Use non-technical product language in a PRD, and describe what a
  user does via "the API" rather than the call it makes.
- State a library workspace's PRD as the capability every workspace
  built on its bricks gets, with the developer as its one persona and
  every journey the developer's.
- List in References the neighbouring PRDs with what each holds, and
  the TDD that serves this one.
- Keep the competitor names the `check-docs` skill refuses in
  `.config/check-docs/names`, adding a name there when one turns up in
  a draft.

**MUST NOT:**

- Use engineering vocabulary (sync/async, reactive, relay, handler,
  primitive, brick) in a PRD.
- Name specific operations (`create-organization`, `submit-payment`,
  etc.) in a PRD.
- Draw internal hops between components in a PRD's sequence diagram.
- Make a counterparty the platform integrates with, or a vendor an
  installation might use, a persona: the first is a stakeholder where
  it appears, the second a worked example.
- Label a PRD `tessl-plugin`.
- Name a consuming workspace in a library workspace's PRD.

**SHOULD:**

- Use the project's vocabulary in TDDs and recipes (`changelog relay`,
  `brick`, `interceptor`); reserve product-shaped phrasing for PRDs.

**MAY:**

- List a call's inputs and outputs as what the call accepts and
  returns.
- Say "all-or-nothing", with a gloss, where atomicity matters — better
  still, what does and does not come up as a result.
- Name a workspace's own personas and its own installation where the
  workspace is itself the product.

## Discussion

The PRD-vs-TDD split is the rule with the highest payoff. A PRD that
names operations and uses engineering vocabulary turns the document
into a TDD-with-different-headers, and the audience it's meant to serve
can't read it without translation. Forcing the register keeps the
documents distinct and readable to their respective audiences. The TDD
covers the same ground, names the operations, and is the engineering
contract, which leaves the PRD free to be product-shaped.

The `check-docs` skill runs the mechanical half: once `docs/prd/`
holds a file, it greps every PRD for the engineering words above and
for backticked operation names, and reports each as a finding against
this recipe.

## References

- [writing-docs](writing-docs.md) — everything under `docs/`.
- [writing-tdds](writing-tdds.md) — the engineering contract behind a
  PRD.
- [mail](../../prd/mail.md) — Mail, a PRD in this shape.
