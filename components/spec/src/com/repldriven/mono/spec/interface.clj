(ns com.repldriven.mono.spec.interface
  (:require
    [com.repldriven.mono.spec.core :as core]))

(def non-empty-string? core/non-empty-string?)

(defn validate
  "Validate data against a Malli schema."
  [schema data]
  (core/validate schema data))

(defn explain
  "Explain validation errors for data against a Malli schema."
  [schema data]
  (core/explain schema data))

(defn humanize
  "Convert validation explanation to human-readable format."
  [explanation]
  (core/humanize explanation))
