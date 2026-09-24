# mono Clojure idioms

Write new Clojure to these conventions — the portable code hygiene a
workspace built on these bricks inherits, stated as how to write.

## Return anomalies, don't throw across a boundary

A component `interface.clj` returns a value or an anomaly — it never
raises, directly or indirectly. Three kinds, each with its constructor
in `error`: `error/fail` for something genuinely wrong, `error/reject`
for a request the system correctly declines, `error/unauthorized` for a
caller with no business asking; the API maps them to 5xx, 4xx and
401/403, and a `CommandResponse` carries the same split. Pick the one
kind that fits the failure, never mix them. Convert an exception at
every Java or library edge with `error/try-nom` (catch all) /
`error/try-nom-ex` (a specific type), never a bare `try`/`catch`, and
thread fallible steps with `let-nom>` / `nom->` / `nom-do>`. Name a
category for the call site (`:http-client/request`) when nobody outside
the process can act on the failure, and for the problem when someone
can — every rejection (`:realworld/article-not-found`), plus the closed
set of storage failures that mean retry — with the call site moving to
the payload as `:operation`; never by failure mode where nothing can act
on the distinction (`:http-client/failed` says no more than
`:http-client/request`). Every payload carries `:message`. A genuinely
unrecoverable `throw` carries `;; nosemgrep: no-raw-throw` on the line
above — the `no-raw-throw` semgrep rule blocks any unmarked one.
See [ADR-0005](../../../docs/adr/0005-error-handling-with-anomalies.md),
[error-handling](../../../docs/recipes/code/error-handling.md).

## Kebab-case keyword keys end-to-end

Use kebab-case keyword keys for all map data inside the system: every
component interface accepts and returns keyword-keyed maps, and
destructuring is `{:keys [...]}`. At the boundaries: the `server` brick
decodes JSON with `keyword` as Muuntaja's `decode-key-fn`; Lancaster is
keyword-keyed by default; `jdbc` uses next.jdbc's
`unqualified-snake-kebab-opts`; an adapter renames a third party's
camelCase at its own boundary; and `http-client/res->edn` is preferred
over the string-keyed `res->body` / `json/read-str`, which are
destructured locally and converted before the data goes anywhere. The
rule governs keys, not values: a code an external standard defines
stays a string (ISO 4217 currency, `"GBP"` not `:currency-gbp`), while
an enum variant internal to the system stays a keyword.
See [ADR-0006](../../../docs/adr/0006-kebab-case-keyword-keys.md).

## IDs and timestamps come from `utility`

`util/uuidv7` for IDs, `util/now` for timestamps and `util/now-rfc3339`
for RFC 3339 strings. Never `random-uuid`, `UUID/randomUUID`,
`Instant/now`, or `System/currentTimeMillis` outside
`components/utility/` — that brick is the only place those primitives
are called, and the `no-raw-time-id` semgrep rule blocks them anywhere
else. For any non-`clojure.core` helper, check `utility` first, then a
helper library re-exported through `utility`; a general helper goes in
`utility`'s sub-namespace and its interface, hoisted there once a
second brick would plausibly want it, never ad hoc in another brick,
and never by pulling the helper library into that brick. A workspace
built on these bricks adds a helper that is not domain-specific here,
released and pulled down by a bump, and keeps one only its domain
would want in a brick of its own.
See [common-helpers](../../../docs/recipes/code/common-helpers.md),
[code-style](../../../docs/recipes/code/code-style.md).

## Requires run innermost to outermost

Order `:require` in nine groups, blank line between each, alphabetical
within: this brick's own `system` namespace; this workspace's extension
namespaces; the upstream workspace's extension namespaces; this file's
own package; the rest of the brick; this workspace's other interfaces;
the upstream's interfaces; external libraries; `clojure.*`. In a flat
component the brick is the package, so the two internal groups collapse
into one, and in this repository the two upstream groups are empty. A
bare require — no `:as`, no `:refer` — takes the bracketed form
`[com.example.ns]`, never unbracketed; unbracketed ones remain in this
repository's own code from before the change, so bracket what you
touch. In a component interface test the SUT takes the own-package
slot, aliased `SUT`, and nothing else from that component is required.
See [code-style](../../../docs/recipes/code/code-style.md).

## Everyday shape

zprint at 80 columns, docstrings wrapped by hand; `cond`, `cond->` and
`cond->>` with each condition and action on consecutive lines and a
blank line between pairs; `cond->` with `util/assoc-some` /
`util/assoc-seq` over chains of optional `assoc`; destructure one map level
per `let` binding, never nested in function arguments, over `get` /
`get-in` chains; each binding, and any form inside `[]`, on one line
for zprint to wrap, wrapped by hand only where it clearly exceeds 80;
a thread macro over intermediate `let` bindings when the chain is the
value; `(fn [x] ...)` over `#(...)`; no `!` suffix on a side-effecting
name; no brick name repeated in a function name within that brick;
short-circuit an interceptor with `sieppari.context/terminate`, never
by setting `:response` or `:error`; a macro declares how clj-kondo
reads it in its own metadata (`{:clj-kondo/lint-as 'clojure.core/let}`)
and gets a hook under `.clj-kondo/hooks/` where no core form matches
its shape.
Commands: `just format`.
See [code-style](../../../docs/recipes/code/code-style.md).

## Comment the why, not the what

`interface.clj` docstrings are the documentation surface: a
one-paragraph ns docstring, and each public fn's contract on the
re-export. Impl defs, private fns and non-interface files stay bare.
Commentary attaches to the name it describes — `^{:doc ...}` metadata
on a `def`, the docstring position on a `defn` — never a `;;` block
floating above the form. Say what the thing does and what its
conditionals do, not why it is shaped that way and not what category
it belongs to. A comment explaining a literal means the literal wants
a name: extract a documented constant instead. An inline `;;` survives
only when it guards a specific edit a reader would otherwise get
wrong; when trimming, delete restatement, control-flow narration and
references to the current change, promoting a load-bearing why to the
docstring. `;; ---` separators belong in `components.clj` and
`interface.clj` only.
See [ADR-0015](../../../docs/adr/0015-comments-and-docstrings.md).

## Tests drive the system with `with-test-system`

Manage system lifecycle in tests with `with-test-system`, which holds
one of `TEST_SYSTEM_PERMITS` permits while its system is up, and assert
anomaly-freeness with `nom-test>`; never `use-fixtures`. A patch-fn as
the second element of `with-test-system`'s binding vector, applied to
the system defs before start, injects an HTTP handler or swaps a
component for a test double. Keep per-brick config at
`test-resources/<brick>/application-test.yml`, and shared test
configuration — container groups, common schemas — in the
`test-resources` brick, which a brick's `:test` alias puts on the
classpath. Start Docker once before a run; no test recipe starts it. A
raw `clojure -M:poly test` against a Docker VM with fewer CPUs than the
host needs the processor cap and the permits set as the test recipe
sets them. Mark a namespace whose tests share state, such as a
`with-redefs`, `^:eftest/synchronized` so its vars run one at a time,
and never one that only boots infrastructure; inject a collaborator
rather than `with-redefs` a var another namespace calls, since the
redefinition is JVM-wide and namespaces run in parallel whatever the
marker says. Assert spans with `with-span-tests` and collect a test
system's spans in memory with the `test-telemetry/otel-sdk` component;
never set clj-otel's default tracer or default OpenTelemetry instance
from a test, and wrap a test that starts `telemetry/otel-sdk` with an
endpoint in `with-exclusive-telemetry`.
Commands: `just start-docker`, `just test`.
See [test-system](../../../docs/recipes/test/test-system.md).
