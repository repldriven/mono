(ns com.repldriven.mono.jdbc.jdbc
  "Every public function of `next.jdbc`, wrapped so it returns an
  anomaly rather than throwing. Mirrors the library namespace for
  namespace, so the two stay easy to compare."
  (:require
    [com.repldriven.mono.jdbc.core :refer
     [opts+ plain default-opts
      sql-nom]]

    [next.jdbc :as jdbc]))

(defn get-datasource
  [spec]
  (sql-nom :jdbc/get-datasource
           "Failed to get datasource"
           (jdbc/get-datasource
            spec)))

(defn get-connection
  ([spec]
   (sql-nom :jdbc/get-connection
            "Failed to get connection"
            (jdbc/get-connection spec)))
  ([spec opts]
   (sql-nom :jdbc/get-connection
            "Failed to get connection"
            (jdbc/get-connection spec (opts+ opts))))
  ([spec user password]
   (sql-nom :jdbc/get-connection
            "Failed to get connection"
            (jdbc/get-connection spec user password)))
  ([spec user password opts]
   (sql-nom :jdbc/get-connection
            "Failed to get connection"
            (jdbc/get-connection spec user password (opts+ opts)))))

(defn prepare
  ([connection sql-params]
   (sql-nom :jdbc/prepare
            "Failed to prepare statement"
            (jdbc/prepare connection sql-params default-opts)))
  ([connection sql-params opts]
   (sql-nom :jdbc/prepare
            "Failed to prepare statement"
            (jdbc/prepare connection sql-params (opts+ opts)))))

(defn with-options
  [connectable opts]
  (jdbc/with-options connectable (opts+ opts)))

(defn with-logging
  ([connectable sql-logger]
   (jdbc/with-logging connectable sql-logger))
  ([connectable sql-logger result-logger]
   (jdbc/with-logging connectable sql-logger result-logger)))

(defn active-tx?
  ([] (jdbc/active-tx?))
  ([connection] (jdbc/active-tx? connection)))

;; ---------------------------------------------------------------------------
;; execution

(defn execute!
  ([stmt]
   (sql-nom :jdbc/execute
            "Failed to execute"
            (plain (jdbc/execute!
                    stmt))))
  ([connectable sql-params]
   (sql-nom :jdbc/execute
            "Failed to execute"
            (plain (jdbc/execute! connectable sql-params default-opts))))
  ([connectable sql-params opts]
   (sql-nom :jdbc/execute
            "Failed to execute"
            (plain (jdbc/execute! connectable sql-params (opts+ opts))))))

(defn execute-one!
  ([stmt]
   (sql-nom :jdbc/execute-one
            "Failed to execute"
            (plain (jdbc/execute-one! stmt))))
  ([connectable sql-params]
   (sql-nom :jdbc/execute-one
            "Failed to execute"
            (plain (jdbc/execute-one! connectable sql-params default-opts))))
  ([connectable sql-params opts]
   (sql-nom :jdbc/execute-one
            "Failed to execute"
            (plain (jdbc/execute-one! connectable sql-params (opts+ opts))))))

(defn execute-batch!
  ([ps param-groups]
   (sql-nom :jdbc/execute-batch
            "Failed to execute batch"
            (jdbc/execute-batch! ps param-groups)))
  ([ps param-groups opts]
   (sql-nom :jdbc/execute-batch
            "Failed to execute batch"
            (jdbc/execute-batch! ps param-groups (opts+ opts))))
  ([connectable sql param-groups opts]
   (sql-nom :jdbc/execute-batch
            "Failed to execute batch"
            (jdbc/execute-batch! connectable sql param-groups (opts+ opts)))))

;; Deliberately unwrapped: `plan` returns a reducible that runs the query when
;; it is reduced, so an anomaly here could only ever report a failure to build
;; the reducible, never the one that matters. Reduce it inside `try-nom` at the
;; call site, or use `execute!`.
(defn plan
  ([stmt] (jdbc/plan stmt))
  ([connectable sql-params] (jdbc/plan connectable sql-params default-opts))
  ([connectable sql-params opts]
   (jdbc/plan connectable
              sql-params
              (opts+
               opts))))

(defn transact
  ([transactable f]
   (sql-nom :jdbc/transact
            "Failed to execute transaction"
            (plain (jdbc/transact transactable f default-opts))))
  ([transactable f opts]
   (sql-nom :jdbc/transact
            "Failed to execute transaction"
            (plain (jdbc/transact transactable f (opts+ opts))))))

;; ---------------------------------------------------------------------------
;; bodies of the interface's macros
;;
;; The binding form is what makes those readable, so they stay macros there —
;; but they expand to these functions rather than inlining anything from here.
;; A macro expands in the CALLER's namespace, where this namespace's private
;; helpers are not visible; and next.jdbc's own `with-transaction` is sugar
;; over `transact` regardless, so there is nothing a macro adds here.
;;
;; The body runs inside the anomaly boundary, so a throw from caller code
;; becomes an anomaly too — the same bargain every `try-nom` makes.

(defn transact-with-options
  [transactable f opts]
  (sql-nom :jdbc/with-transaction
           "Failed to execute transaction"
           (plain (jdbc/with-transaction+options [tx transactable (opts+ opts)]
                                                 (f tx)))))

(defn on-connection
  [connectable f]
  (sql-nom :jdbc/on-connection
           "Failed to execute on connection"
           (plain (jdbc/on-connection [connection connectable]
                                      (f connection)))))

(defn on-connection-with-options
  [connectable f]
  (sql-nom :jdbc/on-connection
           "Failed to execute on connection"
           (plain (jdbc/on-connection+options [connection connectable]
                                              (f connection)))))
