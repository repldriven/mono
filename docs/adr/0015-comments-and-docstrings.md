# 15. Comments and docstrings

<!-- tessl-plugin: idioms -->

## Status

Accepted.

## Context

Code comments and docstrings drift in two directions over time. They
either accumulate as load-bearing context (the *why* a function looks
the way it does — a bug it works around, an invariant it preserves,
a constraint imposed by an upstream library) or they decay into
restatement of the code itself (the *what* — "increment counter",
"call save-record then save-legs"). The first is valuable; the
second is noise that ages worse than the code beside it, since the
code stays correct under refactor while the comment doesn't.

Without a stated rule, we end up with both kinds, and the second
kind crowds out the first. Multi-paragraph explanatory blocks in
front of two-line functions stop being read; rationale that *is*
load-bearing gets lost in the wash. Reviewers also can't tell
whether a comment is required by the codebase's conventions or just
an author's habit.

The Polylith brick model gives us a natural anchor: every brick has
an `interface.clj`. That namespace is the only file external
callers should look at to understand what the brick does. So the
documentation effort concentrates there; supporting code is
expected to be self-explanatory under the names it picks.

## Decision

**Docstrings are the primary form of commentary.** Inline `;;`
comments are exceptional.

The rules:

1. Every `interface.clj` opens with a one-paragraph ns docstring
   saying what the brick is for and what it returns to callers.
2. A public function's docstring lives on the `interface.clj`
   re-export, not the implementation; implementation defs stay bare.
3. Private functions and non-interface implementation files carry no
   docstring and no comments by default — the name is the
   description.
4. An inline `;;` comment is added only for a non-obvious *WHY* (a
   hidden invariant, a workaround for an upstream bug, a behaviour
   that would surprise a reader) — never for the *what* the code
   already says.
5. When trimming a file: delete comments that restate the code,
   narrate control flow, or reference the current change/PR/incident;
   keep only a load-bearing WHY, promoted to the docstring if it
   belongs there.
6. Commentary belongs on the name it describes — a `def`'s in
   `^{:doc ...}` metadata, a function's in its docstring — never in a
   `;;` block floating above the form.
7. A comment explaining a literal is a signal to name the literal.
   Extract a documented constant instead of annotating the value in
   place.
8. A docstring says what the thing does, and what its conditionals do.
   Not why it is shaped that way, and not what category it belongs to.
9. An inline `;;` comment survives only if it guards a specific edit a
   reader would otherwise get wrong.
10. Section separators belong in `components.clj` and `interface.clj`
    only.

### Component interface namespace

Every `interface.clj` opens with a one-paragraph ns docstring that
says what the brick is for and what it returns to its callers.
Two or three sentences. No implementation detail; no list of public
fns (the file already lists them).

```clojure
(ns com.repldriven.mono.cache.interface
  "TTL cache helpers built on `clojure.core.cache.wrapped`.
  Returns an atom-wrapped cache; callers `lookup` with a miss-fn
  to populate, or `evict` to drop a key. nil miss results are not
  cached.")
```

### Public functions

Docstrings live on the **`interface.clj` re-export**, not on the
implementation. The interface is the single contract surface;
readers shouldn't have to chase into `core.clj` / `components.clj`
to find the contract. Implementation defs stay bare.

A one-line purpose, then an `Args:` block that names each param in
one phrase. No "how it works"; the body is the how.

```clojure
;; components/avro/src/.../avro/interface.clj
(defn edn-schema
  "The schema as Clojure data — `{:type :record :fields [...]}` — for
  callers that need to know its shape rather than just use it.
  Returns an anomaly if it cannot be read.

  Args:
  - schema: a compiled Avro schema."
  [schema]
  (serde/edn-schema schema))

;; components/avro/src/.../avro/serde.clj — bare, no docstring.
(defn edn-schema [schema]
  …)
```

For `(def name impl/name)` re-exports, attach the docstring as
metadata so `(doc name)` and tooling pick it up:

```clojure
(def ^{:doc "..."} required-component core/required-component)
```

Acceptable to add one extra short paragraph if there's a non-obvious
contract worth pinning (return-on-anomaly behaviour, idempotency,
side effects). Stop at one paragraph.

### Private functions and non-interface implementation files

Default: no docstring, no comments. The name is the description.
The implementation file says only what the code does — no prose
about what the brick is for, no per-fn purpose blocks, no usage
examples. All of that lives on `interface.clj`.

Only add a docstring when the fn's role inside its own ns isn't
obvious from the name — which usually means the name should change
first.

### Inline `;;` comments

Default: none. The bar for adding one is *non-obvious WHY*. A
hidden invariant, a workaround for an upstream bug, a behaviour
that would surprise a reader who's only seen the surrounding code.

```clojure
;; Between listTopics and here another process may have created it
;; — the same race pulsar's partitioned-topic check guards against.
(try (.get (.all (.createTopics admin [t])))
     (catch Exception e
       (if (instance? TopicExistsException (.getCause e))
         (log/info "Kafka topic already exists:" (.name t))
```

Not acceptable: comments restating the code, comments narrating the
control flow, comments with multi-paragraph backstory. If the
rationale is multi-paragraph, it belongs in an ADR or a recipe, not
in line.

### What to delete

When trimming an existing file:

- Multi-paragraph block comments above a function → keep one
  sentence in the docstring if it captures a non-obvious WHY;
  delete the rest.
- Inline notes describing what the next form does → delete.
- Inline notes referencing the current change/PR/incident
  ("added for the X flow", "fixes issue #123") → delete; that
  context belongs in the commit message and ages out of the file.
- Comments that just restate types or shapes already enforced by
  `:system/config-schema` / spec / malli → delete.

### Commentary attaches to a name

A `;;` block above a `def` is commentary that has come loose from the
thing it describes. `(doc ...)`, editor hover and any doc tooling read
metadata, not the lines above the form, so prose parked there is
invisible to every reader who is not scrolling the file. Attach it:

```clojure
(def
  ^{:doc
    "How many times a failing handler sees a message before it goes
  to the dead-letter producer. Seeking back is the only way to
  redeliver, so without a bound a handler that keeps failing would
  be redelivered forever."}
  default-max-redeliveries
  3)
```

This generalises the `^{:doc ...}` form already required for
`interface.clj` re-exports: it is how a `def` carries documentation
anywhere, not a special case for re-exports. A `defn` uses its
docstring position for the same reason.

### Name the literal instead of commenting it

When a comment exists to explain a literal, the literal wants a name.
A documented constant reaches every call site and is visible to
`(doc ...)`; a comment reaches only the one line it sits beside, and
rots as soon as the value moves.

```clojure
;; before — the rationale is stranded beside one use
;; A JWKS rotates rarely, so ten minutes between fetches is plenty.
{:ttl-ms (* 10 60 1000)}

;; after — the rationale travels with the value
{:ttl-ms jwks-ttl-ms}
```

### What a docstring says

Say what the thing does, then what its conditionals do — those are the
branches a caller has to choose between. Do not justify the design
against alternatives that were not taken; that is commit-message or
ADR material, and it ages badly because the alternatives keep changing
while the behaviour does not. Do not restate the category the thing
belongs to either: every public `def` in a `components.clj` is a
component-kind, so saying so describes the file, not the component.

### The bar for an inline comment

Sharper than "a non-obvious WHY": an inline `;;` earns its place only
when it guards a *specific edit* a reader would otherwise get wrong.
A comment that explains why the code is shaped as it is defends a
decision; a comment that stops a reader inverting a condition, lowering
a timeout or renaming a persisted constant prevents a defect. Only the
second kind is worth the maintenance.

Applying this in practice deletes rationale that reads well. That is
the intended outcome — well-written design defence is still design
defence, and it belongs where decisions are recorded.

### Section separators

A `;; ---` separator block is navigation, not narration, and it is
allowed in exactly two file kinds:

```clojure
;; ---
;; container
;; ---
```

`components.clj` and `interface.clj` are flat lists of independent
definitions with no call graph to read them by, so they are inherently
long and separators are the only structure available. Every other file
has a call graph; if it is long enough to want separators, the fix is
to split it. Reaching for a banner elsewhere is the signal, not the
solution.

## Consequences

Easier:

- Reviewers know the bar without asking. A multi-paragraph block
  comment in a PR diff is a flag, not a default.
- The interface.clj of any brick is the canonical "what does this
  do" surface. Operators and contributors have one place to look.
- Less rotting prose to maintain through refactors. When the code
  changes, the comment doesn't have to be revisited because there
  rarely is one.

Harder:

- Naming has to carry more weight. A function that *needs* a
  comment to be understood is a hint that the name is wrong; the
  rule turns "add a comment" into "rename the fn".
- Genuinely surprising upstream behaviour (broker quirks, an identity
  provider's token semantics) does need to be captured somewhere.
  Inline comments are the right place when they're terse;
  longer-lived rationale moves to ADRs or recipes.
- Picking up the convention is a habit shift for contributors used
  to a "comment everything" style. Reviewers will need to push
  back on overcommented PRs for a while.
