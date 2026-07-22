(ns com.repldriven.mono.spec.interface
  "Thin wrapper over Malli for schema validation and error
  rendering. Exposes a small set of validate/explain/humanize
  helpers and a couple of reusable schema fragments."
  (:require
    [com.repldriven.mono.spec.core :as core]))

(def ^{:doc "Malli schema fragment matching a string of length ≥ 1."}
     non-empty-string?
  core/non-empty-string?)

(defn validate
  "True if `data` matches `schema`.

  Args:
  - schema: a Malli schema.
  - data: the value to validate."
  [schema data]
  (core/validate schema data))

(defn explain
  "Return a Malli explanation map for `data` against `schema`, or
  nil if it validates.

  Args:
  - schema: a Malli schema.
  - data: the value to validate."
  [schema data]
  (core/explain schema data))

(defn humanize
  "Convert a Malli explanation into a human-readable structure.

  Args:
  - explanation: the output of `explain`."
  [explanation]
  (core/humanize explanation))
