(ns com.repldriven.mono.sql.core
  (:refer-clojure :exclude [format])
  (:require
    [com.repldriven.mono.error.interface :as error]
    [honey.sql :as hsql]))

(defn format
  "Format a HoneySQL query map into a [sql & params] vector.
  Returns an anomaly on error."
  [query & opts]
  (error/try-nom :sql/format
                 "Failed to format SQL query"
                 (apply hsql/format query opts)))
