# Writing recipes

<!-- tessl-plugin: docs -->

## Problem

You're writing or editing a recipe under `docs/recipes/` — a procedure
somebody follows from a terminal, or a convention somebody writes to —
and you want it in the shape every other recipe has, with a Rules block
the docs plugin can distil.

## Solution

The shape, and the discipline that keeps a step and its rationale
apart, follow. Formatting, links, mermaid and tone are
[writing-docs](writing-docs.md)'s, which covers everything under
`docs/`.

### What a recipe is made of

Seven sections, in this order, each answering one question and only
one:

- **`## Status`** — has this been run? A procedure only; see below.
- **`## Problem`** — *what* you want, in a sentence or two, opening
  with **You**. Not why it is hard, not what it costs, not what makes
  it awkward.
- **`## Solution`** — *how*. The steps, or the convention, and nothing
  else.
- **`## Failures`** — what it looks like when it did not work, keyed on
  what the reader is looking at. Optional; see below.
- **`## Rules`** — MUST, MUST NOT, MAY. The normative summary, and what
  the tessl plugins distil into agent rules.
- **`## Discussion`** — *why*. Everything the sections above left out.
  Optional, and often the longest.
- **`## References`** — the related recipes and ADRs.

A fact belongs under one of them. Where the same fact is in the
Problem, the Prerequisites and a Rules bullet, two of the three are
copies and they drift apart.

The test is a reader who does not care why: they should be able to stop
at the end of the Rules with everything they need. Where they cannot,
the how is carrying a why, and the why belongs one section further
down where somebody has chosen to read it.

### A procedure says whether it has been run

A recipe describing steps carries a `## Status` as its first section,
opening with one of three words. A recipe describing a convention
carries none: there is nothing to have run.

- **Verified** — the steps have been followed as written, and corrected
  from doing so. A date and what was run, in a line.
- **Untested** — nobody has followed them. Say what they were derived
  from, since that is what a reader is trusting instead.
- **Superseded** — they describe a generation no longer in use. Say
  what still holds, since mechanics often outlive the model around
  them.

A Status says what was run and when, and stops. What went wrong in the
writing belongs in the commit that fixed it, not in a standing account
of how the document got to be right.

Being run and being followed are different things: a procedure written
up after the act is `Untested` until somebody works from the document.
The defects a walkthrough finds are in the writing rather than in the
acts, so a document nobody has followed has not been tested at all.

### Writing steps

A Solution made of steps is copied into a terminal by somebody who is
partway through something and not reading around it. It opens with a
`### Prerequisites` heading holding two things: what must already exist
and who you must be to run each step — one line each, led by the step
it applies to, and no rationale — then the shell the steps assume, as
`export` lines with an example value.

Keep it to what no step supplies. A value a step's own recipe names is
that step's — a capability the contract asks for, a folder id, an
installation code — and a second copy here drifts from it. What belongs
is what the reader has to turn up holding, and what a later step needs
that nothing before it creates, led by that step.

Where a recipe has more than one way in, say where each starts, in a
sentence. Splitting the block into a section per reader writes
everything they both need twice.

```
;; Bad — a block per reader, and the shared lines in both
**From nothing.** Start at step 1, and you need:
- A domain, a payment method, somewhere for a private repository.
**From an established organisation.** Start at step 2. You need the
domain and the repository above, and:
- An IAM member string per capability, a folder id, a four-character
  code.

;; OK — one list, and the entry in a sentence
- Somewhere to keep a private git repository, for the manifests.
- Step 1 — a domain, and a payment method for the billing account.

Start at step 1 to create the organisation. Start at step 2 where one
exists already and can give you a folder.
```

Then:

- Let no command carry a placeholder. A command carrying `<code>` has
  to be edited before it runs, which is how one gets run against the
  wrong thing. What the steps share and you know before starting is
  exported under `### Prerequisites`; a value a step produces is
  exported at that step, beside where you read it off, because somebody
  working down the page does not scroll back to the top to record
  something.
- Put a comment on its own line, never after the command it explains.
  A trailing comment has to be deleted before the line can be pasted,
  and interactive zsh does not treat `#` as a comment by default.
- Name a file in full in every step that touches it. "The same file as
  above" assumes a reader who arrived from above.
- Let each step stand alone. A step that calls a helper defined in
  another step fails for anybody who came back to it in a new shell.
- Say what the output means, especially when nothing appears to have
  happened. A step whose success looks like failure gets repeated.

A step whose damage does not undo — a credential left on a disk, a
delete with nothing behind it — carries a GitHub alert, which renders
as a callout and is hard to skim past:

```
> [!WARNING]
> Delete the key file once the secret store holds it.
```

`[!WARNING]` is the only type used, so that a callout still stands
out.

### A Solution instructs, a Discussion explains

A step says what to do, the command that does it, and what the output
should say. It does not say why it is that way, what is happening
underneath, or what would have happened otherwise. The Discussion is
where both of those live — how it works, and why we did it this way —
and a step that explains itself has taken a paragraph the reader did
not ask for and put it between them and the next command.

The Discussion opens with plain prose, before the first bolded
subsection: what was done, in the first person plural, and then the
machinery that makes it work. Lead with the act — a reader who meets
the mechanism first is holding it with nothing to attach it to.

```
;; Bad — the step carries the mechanism
### 5. Store the values

All three go into one entry so the identifiers travel with the key
rather than through a second channel, and the recipe reads the key
with --rawfile so it never reaches a command line:

;; OK — the step is the instruction, the Discussion holds the rest
### 5. Store the values
```

### The Rules are the only part that travels

A recipe's `## Rules` block is distilled into a Tessl plugin rule,
which is loaded into every agent's context. Everything else in the
recipe is read by somebody who has already decided to open it. So a
`just` recipe named only in a Solution step reaches nobody who did not
already know to look — name it in the bullet whose action it performs.

```
;; Bad — the step runs it, and the rule describes the action without it
- Read the live slot names before naming a composed resource.

;; OK — the name is what has to travel
- Check the rules an agent loads — `just tessl-plugins-check` — before
  trusting what it did with them.
```

Name the recipe and nothing more. Which column to read, what an empty
result means, and which flag it passes belong in the Solution, behind
the link the rule already carries.

### A step's command is a named recipe, not an inline script

A step needing more than a line or two of shell becomes a `just`
recipe. An inline block cannot be named in a Rules bullet, so it never
travels: an agent has no way to reach for it, and a person retypes it
from the page every time.

Wrap what reads. A recipe that only reports — what is not ready, which
policies are live, what an Application last did — is safe to run
without reading it first, and safe to run twice. A step that writes
usually stays inline, where the reader sees what it will do before it
does it — unless the recipe is itself what makes the write safe: one
that refuses a target it cannot verify, or strips the trailing newline a
pasted command would send as part of a credential. Brevity alone is not
a reason.

Where the recipe lives and what it is called are
[justfile-recipes](justfile-recipes.md)'s: the justfile for the domain
it acts on, carrying that domain's prefix.

### Failures are for misleading messages

`## Failures` is an index of the ways this goes wrong that a reader
cannot work out from what they are looking at. The test is one line:
**the message names something other than its cause.**

`verifyManagedZoneDnsNameOwnership` says exactly what is wrong and
gets no entry — the reader will fix it without you. `401
invalid_client` on a rebuilt environment, `Ready: True` over an empty
Secret, a repository reported unreachable because nobody wrote the
version: each points somewhere other than where the fault is, and
misleading is the whole justification for the section. Without that
test it becomes a list of every way the thing has ever broken.

Key each entry on the observable, because that is what the reader
arrived with. Nobody has a container with no version. They have a
repository reported unreachable.

```
;; Bad — keyed on the cause, findable only by somebody who knows it
**A container with no version.** ... so Argo reports the repository
as unreachable.

;; OK — keyed on what the reader is looking at
**A repository reported unreachable.** ... because the entry the
operator syncs holds no version, and both halves succeeded separately.
```

Three things look alike here and belong in three places:

- **A recovery action that undoes a step you just ran** stays in the
  Solution, beside that step. Where the recovery is for the procedure
  having failed rather than for one step, it belongs in Failures, keyed
  on how you know.
- **A failure that reports as something else** goes in Failures.
- **Why the system can fail that way at all** goes in Discussion.

## Rules

**MUST:**

- Structure a recipe as Problem, Solution, Failures, Rules,
  Discussion — what, how, what it looks like when it did not work,
  normative, why. Failures and Discussion appear only where there is
  one to give.
- Open a Problem with the word You, and say what you want in a
  sentence or two.
- Open a procedure's `## Status` with **Verified**, **Untested** or
  **Superseded**. A recipe describing a convention has no Status.
- Key a Failures entry on what the reader observes, never on its
  cause.
- Open a step-based Solution with `### Prerequisites`.
- Export a value a step produces at that step. `### Prerequisites`
  carries what is known before step 1.
- Keep `### Prerequisites` to what no step supplies. A value a step's
  own recipe names is that step's.
- Say where each way into a recipe starts, in a sentence.
- Keep a step to its instruction, its command, and what the output
  should say.
- Open a Discussion with a short unbolded summary of what was done.
- Keep rationale out of the Solution. A reader following steps has
  not asked for it.
- Name a `just` recipe in the Rules bullet whose action it performs.
  The Rules block is the only part of a recipe distilled into agent
  context, so a command named only in a step reaches nobody.
- Make a step's command a `just` recipe where it needs more than a
  line or two of shell. An inline block cannot be named in a bullet,
  so it never travels.

**MUST NOT:**

- Give a failure an entry where the message already names its cause.
- Explain a step inside the step. Mechanism and rationale are the
  Discussion's.
- Split `### Prerequisites` into a block per reader.
- Wrap a step that writes in a `just` recipe for brevity alone. A
  write stays inline, where the reader sees it before running it,
  unless the recipe is what makes the write safe.
- Use any GitHub alert type other than `[!WARNING]`.

## Discussion

We put every recipe in one shape so that the docs plugin can distil it.
The `sync-rules-from-docs` skill reads a recipe's `## Rules` — its
MUST, MUST NOT and SHOULD bullets — and nothing else, and composes the
plugin's rule from what it read. A command named in a step, a caveat
in a Discussion, a value in a Prerequisites block: none of it reaches
an agent unless a Rules bullet carries it, which is why the bullets
name what has to travel and the sections behind the link carry the
rest.

## References

- [writing-docs](writing-docs.md) — formatting, links, mermaid and
  tone, for everything under `docs/`.
- [justfile-recipes.md](justfile-recipes.md) — where a step's recipe
  lives, and what it is called
