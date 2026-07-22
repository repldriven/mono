(ns com.repldriven.mono.utility.interface
  "Project-canonical helpers that aren't in `clojure.core`. Grouped
  internally by domain (collections, string, id, time, uuid, vars).
  Per the common-helpers recipe, every helper has one canonical
  home here; bricks consume them via `util/<name>` rather than
  reimplementing or pulling helper libraries directly."
  (:refer-clojure :exclude [vars])
  (:require
    [com.repldriven.mono.utility.collections :as util.collections]
    [com.repldriven.mono.utility.id :as util.id]
    [com.repldriven.mono.utility.string :as util.string]
    [com.repldriven.mono.utility.time :as util.time]
    [com.repldriven.mono.utility.uuid :as util.uuid]
    [com.repldriven.mono.utility.vars :as vars]))

(def
  ^{:doc
    "Recursively merge maps. When all values at a key are
  maps, recurse; otherwise the last value wins. Args:
  - values: any number of maps to merge."}
  deep-merge
  util.collections/deep-merge)

(def
  ^{:doc
    "Like assoc, but silently drops any kv pair whose value is nil.
  Args:
  - m: the map to update.
  - kvs: alternating key/value pairs; pairs with nil values are skipped."}
  assoc-some
  util.collections/assoc-some)

(def
  ^{:doc
    "Like assoc, but silently drops any kv pair whose value is nil
  or an empty collection. Mirrors `(when (seq v) ...)` semantics —
  use when the value must be both present *and* non-empty.
  Args:
  - m: the map to update.
  - kvs: alternating key/value pairs; pairs with empty/nil values
    are skipped."}
  assoc-seq
  util.collections/assoc-seq)

(def
  ^{:doc
    "First-match search through a nested structure.
  Returns [path value] of the first element matching pred,
  descending into maps/sequentials/sets without recursing into
  matched values; nil when no match. Args:
  - pred: predicate applied at each node.
  - coll: the value to search."}
  deep-some
  util.collections/deep-some)

(def
  ^{:doc
    "Recursively convert every defrecord in `form` to a
  plain map. Args:
  - form: a Clojure value, possibly nested."}
  record->map
  util.collections/record->map)

(def
  ^{:doc
    "Convert YAML-parsed collection types to standard
  Clojure: `flatland.ordered.map.OrderedMap` → hash-map, lazy
  seq → vector. Args:
  - form: a parsed YAML value."}
  yaml-collections->edn-collections
  util.collections/yaml-collections->edn-collections)

(def
  ^{:doc
    "Recursively stringify every map key. Args:
  - form: a Clojure value, possibly nested."}
  keys->strs
  util.collections/keys->strs)

(def
  ^{:doc
    "Recursively keywordize every string map VALUE
  (keys are untouched). Args:
  - form: a Clojure value, possibly nested."}
  val-strs->keywords
  util.collections/val-strs->keywords)

(def ^{:doc "UTF-8 byte-array of a string. Args:
  - s: the string to encode."}
     str->bytes
  util.string/str->bytes)

(def
  ^{:doc
    "Wrap a string in a ByteArrayInputStream. Args:
  - s: the string.
  - encoding (optional): defaults to UTF-8."}
  string->stream
  util.string/string->stream)

(def
  ^{:doc
    "Resolve a source ref. Strings prefixed `classpath:`
  resolve via `io/resource`; everything else is returned as-is for
  the caller to open. Args:
  - source: a path string."}
  resolve-source
  util.string/resolve-source)

(def
  ^{:doc
    "Parse a sequence of `key=value` property strings into
  a keyword map. Args:
  - props: seq of strings."}
  prop-seq->kw-map
  util.string/prop-seq->kw-map)

(def
  ^{:doc
    "Generate a UUIDv7 (RFC 9562) — timestamp-ordered,
  millisecond-precision, sortable. Returns a java.util.UUID."}
  uuidv7
  util.uuid/v7)

(def
  ^{:doc
    "Prefixed monotonic ULID, e.g. `pmt.01jsx6k7h0a…`.
  Sortable by creation time; URL-safe without escaping. Args:
  - prefix: short entity marker (`pmt`, `org`, …)."}
  generate-id
  util.id/generate)

(def
  ^{:doc
    "Current wall-clock time as epoch milliseconds (long).
  Use for persisted `created-at` / `updated-at` values that
  callers expect as int64."}
  now
  util.time/now)

(def
  ^{:doc
    "Current wall-clock time as an RFC-3339 string
  (`2026-04-24T12:34:56.789Z`). Use for persisted timestamps
  stored as strings."}
  now-rfc3339
  util.time/now-rfc3339)

(def
  ^{:doc
    "Current UTC calendar day as an epoch-day (long), derived from
  `now`. Use to compare against persisted epoch-day fields like a
  product version's effective window."}
  today
  util.time/today)

(def
  ^{:doc
    "An epoch-day (long) as an ISO-8601 date string (`2026-06-18`).
  Inverse of `today`; use when surfacing an epoch-day field to people."}
  epoch-day->iso-date
  util.time/epoch-day->iso-date)

(def ^{:doc "Name of a var as a string. Args:
  - v: a Clojure var."} vname
  vars/vname)
