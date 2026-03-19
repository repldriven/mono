(ns com.repldriven.mono.spec.core
  (:require
    [malli.core :as m]
    [malli.error :as me]))

(def non-empty-string? [:string {:min 1}])

(defn validate
  "Validate data against a Malli schema."
  [schema data]
  (m/validate schema data))

(defn explain
  "Explain validation errors for data against a Malli schema."
  [schema data]
  (m/explain schema data))

(defn humanize
  "Convert validation explanation to human-readable format."
  [explanation]
  (me/humanize explanation))
