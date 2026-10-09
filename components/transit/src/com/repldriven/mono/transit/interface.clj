(ns com.repldriven.mono.transit.interface
  "Reading and writing transit+json, for EDN that has to travel or be
  stored as JSON. The four functions mirror the `json` brick's: a
  string and its inverse, a stream and its inverse. The streams are
  byte streams, because transit is defined over bytes; a string is the
  UTF-8 JSON text those bytes carry. Writes are compact
  transit+json — `application/transit+json` — and any conforming
  reader, now or later, reads it back. Keywords, symbols, sets, lists,
  UUIDs, dates, bigints and ratios round-trip without handlers; a
  value transit has no handler for fails the write rather than storing
  something unreadable, and a tag the reader has no handler for reads
  as a tagged value rather than failing. Parse and serialize failures
  come back as `:transit/parse` and `:transit/serialize` anomalies."
  (:refer-clojure :exclude [read])
  (:require
    [com.repldriven.mono.transit.core :as core]))

(defn read-str
  "Parse a transit+json string into Clojure data, or return an
  anomaly.

  Args:
  - s: the transit+json string.
  - options: reader options, passed to the underlying library."
  [s & {:as options}]
  (apply core/read-str s (apply concat options)))

(defn read
  "Parse transit+json from a `java.io.InputStream`, or return an
  anomaly. Reads one value, leaving the stream open.

  Args:
  - in: a `java.io.InputStream`.
  - options: reader options, passed to the underlying library."
  [in & {:as options}]
  (apply core/read in (apply concat options)))

(defn write-str
  "Serialize Clojure data to a transit+json string, or return an
  anomaly.

  Args:
  - x: the value to serialize.
  - options: writer options, passed to the underlying library."
  [x & {:as options}]
  (apply core/write-str x (apply concat options)))

(defn write
  "Serialize Clojure data as transit+json to a `java.io.OutputStream`,
  or return an anomaly. Leaves the stream open.

  Args:
  - x: the value to serialize.
  - out: a `java.io.OutputStream`.
  - options: writer options, passed to the underlying library."
  [x out & {:as options}]
  (apply core/write x out (apply concat options)))
