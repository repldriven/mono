(ns com.repldriven.mono.db.interface
  (:require
    com.repldriven.mono.db.system
    [com.repldriven.mono.db.core :as core]))

(def as-unqualified-lower-maps core/as-unqualified-lower-maps)
(def as-unqualified-kebab-maps core/as-unqualified-kebab-maps)

(def update-count core/update-count)

(defn get-connection [connectable] (core/get-connection connectable))

(defn get-datasource
  [datasource-config]
  (core/get-datasource datasource-config))

(defn execute-one!
  ([datasource sql-params] (core/execute-one! datasource sql-params))
  ([datasource sql-params opts] (core/execute-one! datasource sql-params opts)))

(defn execute!
  ([datasource sql-params] (core/execute! datasource sql-params))
  ([datasource sql-params opts] (core/execute! datasource sql-params opts)))

(defn unique-violation? [anomaly] (core/unique-violation? anomaly))
