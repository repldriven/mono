# mono Clojure idioms

Write new Clojure to these conventions — the portable code hygiene a
workspace built on these bricks inherits, stated as how to write.

## Return anomalies, don't throw across a boundary

A component `interface.clj` returns a value or an anomaly — it never
raises. Three kinds, each with its constructor in `error`: `error/fail`
for something genuinely wrong, `error/reject` for a request the system
correctly declines, `error/unauthorized` for a caller with no business
asking; the API maps them to 5xx, 4xx and 401/403, and a
`CommandResponse` carries the same split. Convert an exception at a
library edge with `error/try-nom` / `error/try-nom-ex`, never a bare
`try`/`catch`, and thread fallible steps with `let-nom>` / `nom->` /
`nom-do>`. Name a category for the call site (`:http-client/request`)
when nobody outside the process can act on the failure, and for the
problem when someone can — every rejection
(`:realworld/article-not-found`), plus the closed set of storage
failures that mean retry — with the call site moving to the payload as
`:operation`. Every payload carries `:message`.
See [ADR-0005](../../../docs/adr/0005-error-handling-with-anomalies.md).

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
