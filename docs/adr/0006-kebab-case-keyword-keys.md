# 6. Kebab-case keyword keys end-to-end

<!-- tessl-plugin: idioms -->

## Status

Accepted.

## Context

Data crosses a lot of boundaries in a system built from these bricks:

- HTTP request and response bodies (JSON, via Reitit + Muuntaja).
- Avro payloads on the message bus (ADR-0004).
- Rows read from a relational database (`jdbc`).
- Clojure maps inside component code.
- Third-party APIs returning JSON with their own conventions.
- Configuration loaded from YAML/EDN.

Each boundary has its own native shape for map keys. Without a
project-wide convention, every boundary becomes a translation point
and every component has to remember which side of which boundary it
is on. Destructuring becomes inconsistent (`{:keys [...]}` here,
`{:strs [...]}` there, `{first-name "first_name"}` for renames). Code
that should read `(:account-id m)` ends up reading
`(get m "accountId")` or worse, depending on where the map came from.

The shortlist:

- **Strings as keys** (whatever case). Closer to the wire. Worked in
  isolation. *Tried it; abandoned it.* The Clojure data libraries —
  Lancaster (Avro), next.jdbc with a kebab-case builder, Muuntaja
  with a `keyword` decode-key-fn, most JSON readers when configured —
  produce keyword-keyed maps by default. Going string-keyed means
  converting *into* strings at every library boundary, which is the
  fight you don't want.
- **camelCase or snake_case keywords.** Possible, but not idiomatic
  Clojure. Lancaster already kebab-cases field names on the way in;
  picking another case means converting from kebab on every Avro
  boundary too.
- **Kebab-case keywords.** Idiomatic Clojure; the path of least
  resistance with every Clojure library involved.

## Decision

We will use kebab-case keyword keys for all map data inside the
system. Every component interface accepts and returns keyword-keyed
maps; destructuring everywhere is `{:keys [...]}`. The rule governs
map *keys*, not values — a code an external standard defines stays a
string (detail below).

Boundary handling, and the one value the rule leaves string-typed:

- **HTTP / JSON via Reitit + Muuntaja.** The `server` brick
  configures Muuntaja's JSON decoder with `keyword` as
  `decode-key-fn`. Request bodies arrive as keyword-keyed maps;
  responses are encoded back to JSON with the default key transform.
- **Avro / Lancaster.** Keyword-keyed by default. No translation
  needed.
- **SQL rows via `jdbc`.** The brick's default options are
  next.jdbc's `unqualified-snake-kebab-opts`: SQL sees snake_case,
  Clojure sees unqualified kebab-case keyword keys.
- **Third-party APIs returning camelCase JSON.** The adapter
  component performs the rename at its boundary, so anything reaching
  internal code is already kebab-keyword.
- **HTTP responses from `http-client`.** Prefer
  `http-client/res->edn`, which parses the response body as Clojure
  data with keyword keys. The string-keyed forms
  (`http-client/res->body`, direct `json/read-str` on a string) exist
  but are rarely used; when they are, callers destructure with string
  keys *locally* and convert before handing the data to anything
  else.
- **Codes an external standard defines.** The rule is about *map
  keys*, not all values. A value that is a code an external standard
  defines stays string-typed, because every wire boundary expects the
  standard's spelling. ISO 4217 currency codes are the usual case:
  `"GBP"`, not `:currency-gbp`, since a keyword form would force
  translation at every wire boundary.

  ```clojure
  ;; OK
  {:account-id "..." :currency "GBP" :amount 1000}

  ;; Not OK
  {:account-id "..." :currency :currency-gbp :amount 1000}
  ```

  Enum variants internal to the system keep the keyword form; only a
  code an external standard defines stays a string.

## Consequences

Easier:

- One destructuring idiom (`{:keys [...]}`) across every component.
- Path of least resistance with the Clojure ecosystem. Lancaster,
  next.jdbc, Reitit, Muuntaja — all line up without translation
  layers.
- Keywords are interned; performance and printing are better than
  strings, and identity comparison is cheap.
- Inside-system data has a single mental model: every map is
  keyword-keyed unless explicitly noted.

Harder:

- The string-keyed escape hatches (`json/read-str`,
  `http-client/res->body`) are easy to misuse if forgotten about. In
  practice they are rarely reached — `http-client/res->edn` covers
  external JSON responses — but the convention is documented rather
  than mechanically enforced.
- External APIs with their own naming (camelCase JSON, snake_case
  payloads coming from elsewhere) need explicit rename logic at the
  adapter. The cost is contained but real.
- Tooling that treats string keys as the canonical form (some
  introspection libraries, some loggers) needs awareness of the
  keyword convention. Not common but it crops up.
