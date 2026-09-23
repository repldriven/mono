# mono docs conventions

How to write or edit anything under `docs/` — formatting, link
hygiene, tone, and what a recipe, an ADR, a TDD and a PRD are each
made of. Deterministic where possible (wrap width, link shape); a
discipline, not a gate, where it isn't (tone).

## Hard-wrap and link hygiene

Hard-wrap markdown at 80 columns under `docs/`, indenting a bullet's
continuation lines by two spaces: a line is measured with its `](...)`
targets removed, and a fenced block, a table row, an HTML tag and a
link's target are not measured at all, which the `check-docs` skill's
wrap check applies, so verify with it. Use the canonical
reference-list link pattern for ADR, recipe, TDD and PRD links —
`[ID](path) — Title`, link intact, title as trailing prose that may
wrap — and keep a relative link inside `docs/` to two levels at most
(`../../adr/...`); never climb three, never put `)` immediately after
a link's closing paren, never wrap link text across lines, never use
inline code as an entire link's text. Reach a non-markdown file outside
`docs/` with a repo-root link, whose text is the file's name —
`[values.yaml](/infra/helm/…)` — since GitHub resolves a leading `/`
against the repository root and a whole path as link text would not
fit. Markdown stays relative, which works in an editor as well as on
GitHub; a generic filename (`deps.edn`) and a path carrying a
placeholder both stay in backticks, one naming a shape rather than a
file and the other resolving to nothing. Inside mermaid labels, notes,
and arrow text, replace `;` with `,`, `—`, `.`, or `<br/>` — mermaid
treats `;` as a statement separator and GitHub fails to render the
block. A mermaid line MAY exceed 80 characters when the diagram reads
more clearly as one line.
See [writing-docs](../../../docs/recipes/practices/writing-docs.md).

## A doc may be formatted, so the tooling tolerates formatting

Carry `<!-- tessl-plugin: <name> -->` after the title of a recipe or
ADR a plugin rule distils, and in no TDD or PRD. Anywhere between the
title and the first section is found and the exact line is not
load-bearing: a formatter puts a blank line after a heading, so a
label pinned to one line moves the first time the file is saved, and a
parser reading a fixed line reports the doc as unlabelled —
indistinguishable from one nobody labelled, and silent. Never move a
label to satisfy a script: if discovery cannot find a labelled doc,
the script is what is wrong.
See [writing-docs](../../../docs/recipes/practices/writing-docs.md).

## A recipe is seven sections, each answering one question

Structure a recipe as Problem, Solution, Failures, Rules, Discussion —
what, how, what it looks like when it did not work, normative, why —
with Failures and Discussion appearing only where there is one to give.
Open the Problem with the word You, saying what you want in a sentence or
two. Open a procedure's `## Status` with **Verified**, **Untested** or
**Superseded** — a procedure written up after the act is **Untested**
until somebody has worked from the document — saying what was run and
when, what the steps were derived from, or what still holds, and
stopping there; a recipe describing a convention has none.

Open a step-based Solution with `### Prerequisites`, which carries what
is known before step 1 and nothing a step supplies — a value a step's
own recipe names is that step's — as one block, never one per reader,
each line led by the step it applies to, then the shell the steps
assume as `export` lines with an example value. Say where each way into
a recipe starts, in a sentence. Export a value a step produces at that
step. Keep a step to its instruction, its command and what the output
should say, naming a file in full in every step that touches it and
letting each step stand alone in a new shell: never explain a step
inside the step, since mechanism and rationale are the Discussion's,
which opens with a short unbolded summary of what was done, in the
first person plural. Let no command carry a placeholder, put a comment
on its own line rather than after the command it explains, and mark a
step whose damage does not undo with `[!WARNING]`, the only GitHub
alert type used.

Key a Failures entry on what the reader observes, never on its cause,
and give no entry to a failure whose message already names it. Keep a
recovery for one step beside that step in the Solution, a failure that
reports as something else in Failures, and why the system can fail
that way in Discussion.

State in Rules every MUST, MUST NOT or MAY the Solution, Failures or
Discussion carries, and name a `just` recipe in the Rules bullet whose
action it performs — the name and nothing more, since which column to
read, what an empty result means and which flag it passes belong in
the Solution. The Rules block is the only part of a recipe distilled
into agent context, so a norm stated anywhere else, or a command named
only in a step, reaches nobody — which is also why a step needing more
than a line or two of shell becomes a recipe rather than an inline
block, since an inline block cannot be named in a bullet and so never
travels. Wrap what reads: a step that only reports is safe to run
unread and safe to run twice. A step that writes usually stays inline,
where the reader sees it before running it, and never goes behind a
recipe for brevity alone: only where the recipe is itself what makes
the write safe.
See [writing-recipes](../../../docs/recipes/practices/writing-recipes.md).

## An ADR is Status, Context, Decision, Consequences

Name an ADR `NNNN-slug.md` with the next free number — never reuse one — and
title it `# N. Title` with the decision as the title. Structure it as Status,
Context, Decision, Consequences. Give the Status one word as its own paragraph —
**Proposed**, **Accepted**, or **Superseded by** and a link to the successor —
and the Context the property wanted, what happens without it, and the shortlist
as `**The option.**` bullets, a rejected option saying why in its sentence and
the chosen one last. Open the Decision with the decision in one paragraph and,
where it has parts, introduce them with one unwrapped line ending in a colon and
a list, normative and in the imperative, keeping every part in that list as an
item or its indented continuation, since only the lead and the list are
distilled and a wrapped colon line loses the list; a long Decision MAY carry
`###` sub-sections, one per aspect, where the parts have rationale of their own,
and is then distilled whole; a worked example follows the rule and is never
presented as it. Write the Consequences as `Easier:` and `Harder:` lists, and
acknowledge drift under Harder, naming the audit that makes it visible.
Supersede a decision that no longer holds with a new number rather than
rewriting it: mark the replaced ADR **Superseded by** its successor and leave
its Context, Decision and Consequences as they were.
See [writing-adrs](../../../docs/recipes/practices/writing-adrs.md).

## A TDD is a Status banner and six sections

Structure a TDD as a Status banner, Objective, Background, Proposed
Solution, Alternatives Considered, Known Limitations, References, and
give it no `tessl-plugin` label. Open the banner, a blockquote under
the title, with **proposal** or **implemented**; a proposal's banner
names what exists, says the Proposed Solution is the build list, and
names the section that says what comes first, and an implemented
TDD's is the one line. Open the Objective's scope paragraphs with
`In scope:` and `Out of scope:`, linking the document that decides
each thing left out. Keep Background to what exists, as bullets
opening with a bold label and naming where the thing lives, and
Proposed Solution to what will be built, one `###` per design area
naming the files it touches and the operations, anomalies, config
keys and schemas it adds, ending with a first-slice section and
`### Tests`, one bullet per brick or base saying what its tests cover.
List each alternative as `**The alternative.** Rejected:` with its
reason in that sentence, saying which part where one is taken in
part, and Known Limitations as bullets opening with a bold label. List
the PRD the design serves first in References, then sibling TDDs,
ADRs, recipes and external specifications, each as
`[id](path) — gloss` saying what the document gives the design. Once
implemented, rename Proposed Solution to Solution and the banner with
it. In a library
workspace, name no consuming workspace: the design serves every
workspace built on the bricks.
See [writing-tdds](../../../docs/recipes/practices/writing-tdds.md).

## A PRD is eight sections in the product register

Structure a PRD as Objective, Users and stakeholders, Goals,
Non-goals, Functional scope, User journeys, Open questions,
References, and give it no `tessl-plugin` label. Say in the Objective
where the line between this PRD and its neighbours falls. Take its
personas from the workspace's platform PRD where one exists, each
described as what they do and care about, and never make a
counterparty the platform integrates with, or a vendor an installation
might use, a persona: the first is a stakeholder where it appears, the
second a worked example. Write Goals, Non-goals and Open questions as
bold-labelled bullets, a non-goal naming where the thing lives if it
lives somewhere and an open question saying what is assumed meanwhile.
Draw a user journey as a numbered `###` section holding a mermaid
sequence diagram of user-visible beats — never an internal hop between
components — and a paragraph reading it. List in References the
neighbouring PRDs with what each holds, and the TDD that serves this
one. Use non-technical product language: no sync/async, reactive,
relay, handler, primitive or brick, and no operation names
(`create-organization`, `submit-payment`) — a user "uses the API to"
do a thing, and the call MAY be given what it accepts and returns;
where atomicity matters, say "all-or-nothing" with a gloss, or better
what does and does not come up as a result. In a library workspace,
state the PRD as the capability every workspace built on its bricks
gets, with the developer as its one persona and every journey the
developer's, and name no consumer; a workspace that is itself the
product MAY name its own personas and its own installation. Keep the
competitor names the `check-docs` skill refuses in
`.config/check-docs/names`, adding a name there when one turns up in a
draft. Reserve the project's vocabulary (`changelog relay`, `brick`,
`interceptor`) for TDDs and recipes.
See [writing-prds](../../../docs/recipes/practices/writing-prds.md).

## Tone, maturity claims, and plain writing

Don't describe these bricks, a workspace built on them, or their
components as "battle-tested", "production-proven", or similar
maturity claims — none has production miles yet. Don't name a specific
competitor in any doc; cite a public spec, RFC, or standard instead
when a reference is useful, and keep the names the `check-docs` skill
refuses in the workspace's `.config/check-docs/names`. Don't pin a doc
to a specific count of repo artefacts (ADRs, recipes, bricks) or to a
relative-time framing ("recently", "as of…") — both age worse than the
prose around them — unless the count or date is external rather than
project state, a regulator threshold or a fixed-cardinality enum.
Frame a code-quality rule as a principle and
discipline rather than a mechanical CI gate, and acknowledge drift in
an ADR's Harder consequences.

Write in the fewest words that stay precise — the facts and the
instructions, and nothing else. Never state the same fact under two
headings, and never say the same thing twice in other words. Don't say
that anything earns, deserves, or is worth its place; don't raise an
objection nobody made in order to answer it ("not arbitrary",
"deliberate rather than lax"); don't close a passage with a sentence
that generalises it and carries no fact; don't gesture at a thing that
has a name ("what pays for it" for a billing account, "where its
manifests live" for a repository); and don't narrate the writing — what
the page used to say, which recipes it replaces, or how a section reads
now. Title a section or a bold paragraph label in the words somebody
would search for — "Known limitations", not "What is not yet true, and
should not be assumed" — never an epigram.
See [writing-docs](../../../docs/recipes/practices/writing-docs.md).
