(ns com.repldriven.mono.db.interface
  "next.jdbc facade with anomaly-friendly error handling. Exposes
  datasource construction, statement execution, common
  result-set builders, and a unique-violation predicate.
  Component-kinds for `:db/datasources` and `:db/datasource` are
  registered via this brick's `system` namespace."
  (:require
    com.repldriven.mono.db.system
    [com.repldriven.mono.db.core :as core]))

(def
  ^{:doc
    "next.jdbc result-set builder that returns unqualified
  lower-cased keyword keys."}
  as-unqualified-lower-maps
  core/as-unqualified-lower-maps)

(def
  ^{:doc
    "next.jdbc result-set builder that returns unqualified
  kebab-cased keyword keys."}
  as-unqualified-kebab-maps
  core/as-unqualified-kebab-maps)

(def
  ^{:doc
    "Key under which next.jdbc returns the affected-row
  count from `execute-one!`/`execute!`."}
  update-count
  core/update-count)

(defn get-connection
  "Get a `java.sql.Connection` from a connectable, suitable for
  use with `with-open`.

  Args:
  - connectable: a datasource, db-spec, or other next.jdbc
    connectable."
  [connectable]
  (core/get-connection connectable))

(defn get-datasource
  "Build a JDBC datasource from a config map, or return an anomaly.

  Args:
  - datasource-config: next.jdbc datasource config map."
  [datasource-config]
  (core/get-datasource datasource-config))

(defn execute-one!
  "Execute a SQL statement and return a single row map, or an
  anomaly.

  Args:
  - datasource: a next.jdbc datasource.
  - sql-params: `[sql & params]` vector.
  - opts: optional next.jdbc options map."
  ([datasource sql-params] (core/execute-one! datasource sql-params))
  ([datasource sql-params opts] (core/execute-one! datasource sql-params opts)))

(defn execute!
  "Execute a SQL statement and return all result rows, or an
  anomaly.

  Args:
  - datasource: a next.jdbc datasource.
  - sql-params: `[sql & params]` vector.
  - opts: optional next.jdbc options map."
  ([datasource sql-params] (core/execute! datasource sql-params))
  ([datasource sql-params opts] (core/execute! datasource sql-params opts)))

(defn unique-violation?
  "True if `anomaly` wraps a Postgres unique-constraint violation
  (SQLState 23505).

  Args:
  - anomaly: a value to test."
  [anomaly]
  (core/unique-violation? anomaly))
