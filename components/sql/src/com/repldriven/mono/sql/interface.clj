(ns com.repldriven.mono.sql.interface
  "HoneySQL formatter wrapped to return anomalies instead of throwing.
  Thin shim so callers can `nom->`-thread query construction without
  catching exceptions."
  (:refer-clojure :exclude [format])
  (:require
    [com.repldriven.mono.sql.core :as core]))

(defn format
  "Format a HoneySQL query map into a `[sql & params]` vector or an
  anomaly.

  Args:
  - query: a HoneySQL query map.
  - opts: optional HoneySQL format options."
  [query & opts]
  (apply core/format query opts))
