# mono Polylith framework

How to use Polylith itself in a workspace built on these bricks —
independent of what the bricks do.

## Wrap every third-party library in exactly one brick

Give every third-party library — Java or Clojure, excluding
`clojure.core`, `clojure.string`, `clojure.set`, `clojure.walk`,
`clojure.spec.alpha` and the rest of core — exactly one brick as its
consumer, and reach it from any other brick through that wrapper's
`interface.clj`, never the library. Adding a library either creates a
new component or extends an existing one; when in doubt, a new
single-purpose component. `clj -M:poly libs` prints libraries as rows
and bricks as columns, and the principle is one X per row: a row with
more is a target for cleanup during ordinary development, consolidated
behind one component or accepted for a real reason — a discipline, not
a CI gate.
See [ADR-0011](../../../docs/adr/0011-one-component-per-third-party-library.md).
