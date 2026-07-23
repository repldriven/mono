(ns com.repldriven.mono.jdbc.interface
  "The whole of next.jdbc, under mono's rules.

  Every public function of `next.jdbc` and `next.jdbc.sql`, so
  nothing sends a caller looking for the library directly, but each
  wrapped so it returns an anomaly rather than throwing, per this
  workspace's error contract.

  Two things differ from calling next.jdbc yourself:

  - Naming is defaulted. Every call merges
    `next.jdbc/unqualified-snake-kebab-opts`, so SQL sees snake_case
    and Clojure sees unqualified kebab-case keyword keys. Pass your
    own opts to override, per call.
  - `:next.jdbc/update-count` comes back as `:jdbc/update-count`, so
    the library's namespace stays out of caller code while the key
    stays qualified — it is driver metadata, not a column.

  Anomalies caused by the database carry `:sql-state` and
  `:vendor-code` on their payload, so a caller who knows what a code
  means can act on it without unpacking the exception. `sql-state`,
  `unique-violation?` and `constraint-violation?` below read them.

  `plan` is the one function that is not wrapped; see its docstring.

  Component-kinds for `:jdbc/datasource` and `:jdbc/datasources` are
  registered via this brick's `system` namespace."
  (:require
    com.repldriven.mono.jdbc.system

    [com.repldriven.mono.jdbc.core :as core]
    [com.repldriven.mono.jdbc.jdbc :as jdbc]
    [com.repldriven.mono.jdbc.sql :as sql]
    [com.repldriven.mono.jdbc.sql-state :as sql-state]))

(def
  ^{:doc
    "Opts merged into every call: snake_case in SQL,
  unqualified kebab-case in Clojure."}
  default-opts
  core/default-opts)

(def
  ^{:doc
    "Key under which a statement with no result set reports the
  number of rows it affected. next.jdbc's own
  `:next.jdbc/update-count`, re-namespaced to this brick."}
  update-count
  :jdbc/update-count)

;; ---------------------------------------------------------------------------
;; connections and datasources

(defn get-datasource
  "Return a `javax.sql.DataSource` for `spec`, or an anomaly.

  Args:
  - spec: a db-spec map, jdbc URL string, or existing DataSource."
  [spec]
  (jdbc/get-datasource spec))

(defn get-connection
  "Return a `java.sql.Connection`, or an anomaly. Suitable for
  `with-open`; prefer `on-connection` where the scope is a body.

  Args:
  - spec: a connectable.
  - user, password: credentials, when not carried on the spec.
  - opts: next.jdbc options."
  ([spec] (jdbc/get-connection spec))
  ([spec opts] (jdbc/get-connection spec opts))
  ([spec user password] (jdbc/get-connection spec user password))
  ([spec user password opts] (jdbc/get-connection spec user password opts)))

(defn prepare
  "Return a `java.sql.PreparedStatement` for `sql-params`, or an
  anomaly. Pair with `execute-batch!`.

  Args:
  - connection: an open connection.
  - sql-params: a vector of SQL string followed by parameters.
  - opts: next.jdbc options."
  ([connection sql-params] (jdbc/prepare connection sql-params))
  ([connection sql-params opts] (jdbc/prepare connection sql-params opts)))

(defn with-options
  "Wrap `connectable` so `opts` apply to every call made through it.

  Args:
  - connectable: a datasource, connection, or connectable.
  - opts: next.jdbc options."
  [connectable opts]
  (jdbc/with-options connectable opts))

(defn with-logging
  "Wrap `connectable` so each statement is passed to the loggers.

  Args:
  - connectable: a datasource, connection, or connectable.
  - sql-logger: called with the SQL and parameters before execution.
  - result-logger: called with the result after execution."
  ([connectable sql-logger] (jdbc/with-logging connectable sql-logger))
  ([connectable sql-logger result-logger]
   (jdbc/with-logging connectable sql-logger result-logger)))

(defn active-tx?
  "True when called inside a `with-transaction` body.

  Args:
  - connection: the connection to check; defaults to the current
    transaction's."
  ([] (jdbc/active-tx?))
  ([connection] (jdbc/active-tx? connection)))

;; ---------------------------------------------------------------------------
;; execution

(defn execute!
  "Execute `sql-params` and return a vector of result maps, or an
  anomaly. Statements with no result set return a single map keyed
  by `:jdbc/update-count`.

  Args:
  - connectable: a datasource, connection, or connectable.
  - sql-params: a vector of SQL string followed by parameters.
  - opts: next.jdbc options, merged over `default-opts`."
  ([stmt] (jdbc/execute! stmt))
  ([connectable sql-params] (jdbc/execute! connectable sql-params))
  ([connectable sql-params opts] (jdbc/execute! connectable sql-params opts)))

(defn execute-one!
  "Execute `sql-params` and return the first result map, or an
  anomaly. Statements with no result set return a map keyed by
  `:jdbc/update-count`.

  Args:
  - connectable: a datasource, connection, or connectable.
  - sql-params: a vector of SQL string followed by parameters.
  - opts: next.jdbc options, merged over `default-opts`."
  ([stmt] (jdbc/execute-one! stmt))
  ([connectable sql-params] (jdbc/execute-one! connectable sql-params))
  ([connectable sql-params opts]
   (jdbc/execute-one! connectable sql-params opts)))

(defn execute-batch!
  "Execute one statement once per parameter group, returning a
  vector of update counts, or an anomaly.

  Args:
  - ps: a prepared statement, or a connectable plus `sql`.
  - param-groups: a sequence of parameter vectors.
  - opts: next.jdbc options, merged over `default-opts`."
  ([ps param-groups] (jdbc/execute-batch! ps param-groups))
  ([ps param-groups opts] (jdbc/execute-batch! ps param-groups opts))
  ([connectable sql param-groups opts]
   (jdbc/execute-batch! connectable sql param-groups opts)))

(defn plan
  "Return a reducible that streams rows as they arrive, without
  realising a result set.

  The only function here that does NOT return an anomaly: the query
  runs when the reducible is reduced, not when it is built, so any
  anomaly this could return would describe the wrong moment. Reduce
  it inside `error/try-nom` at the call site, or use `execute!`.

  Args:
  - connectable: a datasource, connection, or connectable.
  - sql-params: a vector of SQL string followed by parameters.
  - opts: next.jdbc options, merged over `default-opts`."
  ([stmt] (jdbc/plan stmt))
  ([connectable sql-params] (jdbc/plan connectable sql-params))
  ([connectable sql-params opts] (jdbc/plan connectable sql-params opts)))

(defn transact
  "Run `f` inside a transaction, returning its value or an anomaly.
  The function form of `with-transaction`.

  Args:
  - transactable: a datasource, connection, or connectable.
  - f: a function of one argument, the transacted connectable.
  - opts: next.jdbc options, merged over `default-opts`."
  ([transactable f] (jdbc/transact transactable f))
  ([transactable f opts] (jdbc/transact transactable f opts)))

;; ---------------------------------------------------------------------------
;; friendly SQL functions

(defn insert!
  "Insert one row, returning it as a map, or an anomaly.

  Args:
  - connectable: a datasource, connection, or connectable.
  - table: the table name, as a keyword.
  - key-map: column keyword to value.
  - opts: next.jdbc options, merged over `default-opts`."
  ([connectable table key-map] (sql/insert! connectable table key-map))
  ([connectable table key-map opts]
   (sql/insert! connectable table key-map opts)))

(defn insert-multi!
  "Insert several rows in one statement, returning them, or an
  anomaly.

  Args:
  - connectable: a datasource, connection, or connectable.
  - table: the table name, as a keyword.
  - hash-maps: a sequence of column-to-value maps; or `cols` and
    `rows` given separately.
  - opts: next.jdbc options, merged over `default-opts`."
  ([connectable table hash-maps]
   (sql/insert-multi! connectable table hash-maps))
  ([connectable table hash-maps opts]
   (sql/insert-multi! connectable table hash-maps opts))
  ([connectable table cols rows opts]
   (sql/insert-multi! connectable table cols rows opts)))

(defn query
  "Execute `sql-params` and return a vector of result maps, or an
  anomaly.

  Args:
  - connectable: a datasource, connection, or connectable.
  - sql-params: a vector of SQL string followed by parameters.
  - opts: next.jdbc options, merged over `default-opts`."
  ([connectable sql-params] (sql/query connectable sql-params))
  ([connectable sql-params opts] (sql/query connectable sql-params opts)))

(defn find-by-keys
  "Return every row of `table` matching `key-map`, or an anomaly.

  Args:
  - connectable: a datasource, connection, or connectable.
  - table: the table name, as a keyword.
  - key-map: column keyword to value, or `:all` for every row.
  - opts: next.jdbc options, merged over `default-opts`."
  ([connectable table key-map] (sql/find-by-keys connectable table key-map))
  ([connectable table key-map opts]
   (sql/find-by-keys connectable table key-map opts)))

(defn get-by-id
  "Return the row of `table` with primary key `pk`, or an anomaly.

  Args:
  - connectable: a datasource, connection, or connectable.
  - table: the table name, as a keyword.
  - pk: the primary key value.
  - pk-name: the primary key column, when not `:id`.
  - opts: next.jdbc options, merged over `default-opts`."
  ([connectable table pk] (sql/get-by-id connectable table pk))
  ([connectable table pk opts] (sql/get-by-id connectable table pk opts))
  ([connectable table pk pk-name opts]
   (sql/get-by-id connectable table pk pk-name opts)))

(defn update!
  "Update rows of `table` matching `where-params`, or return an
  anomaly.

  Args:
  - connectable: a datasource, connection, or connectable.
  - table: the table name, as a keyword.
  - key-map: column keyword to new value.
  - where-params: a column-to-value map, or a SQL fragment vector.
  - opts: next.jdbc options, merged over `default-opts`."
  ([connectable table key-map where-params]
   (sql/update! connectable table key-map where-params))
  ([connectable table key-map where-params opts]
   (sql/update! connectable table key-map where-params opts)))

(defn delete!
  "Delete rows of `table` matching `where-params`, or return an
  anomaly.

  Args:
  - connectable: a datasource, connection, or connectable.
  - table: the table name, as a keyword.
  - where-params: a column-to-value map, or a SQL fragment vector.
  - opts: next.jdbc options, merged over `default-opts`."
  ([connectable table where-params]
   (sql/delete! connectable table where-params))
  ([connectable table where-params opts]
   (sql/delete! connectable table where-params opts)))

(defn aggregate-by-keys
  "Return the result of `aggregate` over rows matching `key-map`,
  or an anomaly.

  Args:
  - connectable: a datasource, connection, or connectable.
  - table: the table name, as a keyword.
  - aggregate: the SQL aggregate expression, e.g. \"count(*)\".
  - key-map: column keyword to value, or `:all` for every row.
  - opts: next.jdbc options, merged over `default-opts`."
  ([connectable table aggregate key-map]
   (sql/aggregate-by-keys connectable table aggregate key-map))
  ([connectable table aggregate key-map opts]
   (sql/aggregate-by-keys connectable table aggregate key-map opts)))

;; ---------------------------------------------------------------------------
;; macros

(defmacro with-transaction
  "Run `body` with `sym` bound to a transacted connectable,
  committing on success and rolling back on exception. Returns the
  body's value, or an anomaly — including when the body itself
  throws.

  Args:
  - binding: `[sym transactable opts]`, opts merged over
    `default-opts`."
  [[sym transactable opts] & body]
  `(jdbc/transact ~transactable (fn [~sym] ~@body) ~opts))

(defmacro with-transaction+options
  "Like `with-transaction`, but `sym` is bound to a connectable that
  carries the options, so calls made through it inherit them.

  Args:
  - binding: `[sym transactable opts]`, opts merged over
    `default-opts`."
  [[sym transactable opts] & body]
  `(jdbc/transact-with-options ~transactable (fn [~sym] ~@body) ~opts))

(defmacro on-connection
  "Run `body` with `sym` bound to a connection, reusing an existing
  one where `connectable` already is one. Returns the body's value,
  or an anomaly.

  Args:
  - binding: `[sym connectable]`."
  {:clj-kondo/lint-as 'clojure.core/with-open}
  [[sym connectable] & body]
  `(jdbc/on-connection ~connectable (fn [~sym] ~@body)))

(defmacro on-connection+options
  "Like `on-connection`, but `sym` is bound to a connection that
  carries the options, so calls made through it inherit them.

  Args:
  - binding: `[sym connectable]`."
  {:clj-kondo/lint-as 'clojure.core/with-open}
  [[sym connectable] & body]
  `(jdbc/on-connection-with-options ~connectable (fn [~sym] ~@body)))

;; ---------------------------------------------------------------------------
;; classifying failures
;;
;; These read an anomaly this brick produced, rather than wrapping anything, so
;; they live outside `core`. They match on SQLSTATE, so they hold for any
;; driver — no vendor exception class, and no driver on the classpath.

(defn sql-state
  "The SQLSTATE carried by `anomaly`, or nil when it was not caused
  by a database failure. Also on the payload as `:sql-state`,
  alongside `:vendor-code`.

  Args:
  - anomaly: an anomaly returned by any function here."
  [anomaly]
  (sql-state/state anomaly))

(defn unique-violation?
  "True when `anomaly` was caused by a unique-constraint violation,
  SQLSTATE 23505.

  Args:
  - anomaly: an anomaly returned by any function here."
  [anomaly]
  (sql-state/unique-violation? anomaly))

(defn constraint-violation?
  "True when `anomaly` was caused by any integrity-constraint
  violation — SQLSTATE class 23, so unique, foreign key, not-null
  and check constraints alike.

  Args:
  - anomaly: an anomaly returned by any function here."
  [anomaly]
  (sql-state/constraint-violation? anomaly))
