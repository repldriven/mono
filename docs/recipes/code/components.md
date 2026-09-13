# Components

<!-- tessl-plugin: framework -->

## Problem

You want to add or modify a Polylith component.

## Solution

A component under `components/` has a small public surface —
`interface.clj` — that delegates to internal namespaces. Other code only
ever touches the interface; the implementation is private to the
component.

### File layout

```
components/<brick>/
  src/com/repldriven/mono/<brick>/
    interface.clj    ; public API, delegates to other namespaces
    core.clj         ; primary implementation
    domain.clj       ; (often) data shapes, validation
    store.clj        ; (often) persistence
    system.clj       ; (when registering system components)
  deps.edn
```

A workspace built on these bricks puts its own top namespace under
`src/`; the shape is the same.

`interface.clj` re-exports public functions, each carrying the contract
its callers read:

```clojure
(ns com.repldriven.mono.<brick>.interface
  "One paragraph on what the component is for."
  (:require
    [com.repldriven.mono.<brick>.core :as core]))

(defn do-the-thing
  "What it returns, and the anomaly kinds it returns instead."
  [x]
  (core/do-the-thing x))
```

If your component contributes to a system definition, see
[system-components.md](system-components.md) for the registration
patterns, and [ADR-0015](../../adr/0015-comments-and-docstrings.md) for
what the interface's docstrings carry.

### Accessing other components

A component reaches another's API through its `interface.clj`, never
through internal namespaces — but that reaching happens from this
component's own `core.clj` (or `domain.clj`, `store.clj`, etc.), never
from its own `interface.clj`. `interface.clj` requires only this
component's own namespaces — nothing from any other brick, not even
that brick's `interface.clj`, and not even a library-wrapper brick like
`error` or `utility`:

```clojure
;; OK, from core.clj (or domain.clj, store.clj, ...)
[com.repldriven.mono.error.interface :as error]

;; Not OK, from core.clj: reaching into a peer's internals
[com.repldriven.mono.error.core :as error-core]

;; Not OK, from interface.clj: reaching into any other brick at
;; all, even through its interface — that's composition, and it
;; belongs one level down, in core.clj
[com.repldriven.mono.error.interface :as error]
```

Don't add other components to a brick's `deps.edn`. Polylith resolves
inter-component dependencies through interface namespace references in
source code. Only third-party libraries belong in a brick's `deps.edn`,
and each of those in exactly one brick — see
[ADR-0011](../../adr/0011-one-component-per-third-party-library.md).

## Rules

**MUST:**

- Components define their public API in `interface.clj`.
- `interface.clj` delegates to other namespaces in the same component,
  and only to namespaces in the same component.
- Other components are accessed via their `interface.clj` — from this
  component's own `core`/`domain`/`store`/etc., never from
  `interface.clj` itself.
- If the symbol you need isn't yet on another component's
  `interface.clj`, add it there first — never reach around the
  interface to get it.

**MUST NOT:**

- Implement logic directly in `interface.clj`.
- Reach into another component's internal namespaces.
- Require any other component from `interface.clj` — including that
  component's own `interface.clj`.
- Include other components in `deps.edn`.

## Discussion

The brick boundary is the workspace's only mechanism for hiding
implementation. If components reach across `interface.clj` or pull each
other in via `deps.edn`, the boundary stops meaning anything and
refactors get painful. The rules look pedantic; they pay off when you
can change a brick's internals freely because no caller depended on
them.

The split between `interface.clj` (delegates) and the rest (implements)
lets you reorganise internals without rewriting any of the callers. The
cost is one more level of indirection in the function-call graph. We
accept that cost.

## References

- [ADR-0011](../../adr/0011-one-component-per-third-party-library.md) —
  One component per third-party library
- [ADR-0015](../../adr/0015-comments-and-docstrings.md) — Comments and
  docstrings
- [bases.md](bases.md)
- [system-components.md](system-components.md)
- [Polylith documentation](https://polylith.gitbook.io/polylith)
