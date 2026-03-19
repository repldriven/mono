(ns com.repldriven.mono.sql.interface
  (:refer-clojure :exclude [format])
  (:require
    [com.repldriven.mono.sql.core :as core]))

(defn format
  "Format a HoneySQL query map into a [sql & params] vector.
  Returns an anomaly on error."
  [query & opts]
  (apply core/format query opts))
