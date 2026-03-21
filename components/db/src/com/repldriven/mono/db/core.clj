(ns com.repldriven.mono.db.core
  (:require
    [com.repldriven.mono.error.interface :as error :refer [try-nom]]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs])
  (:import
    org.postgresql.util.PSQLException))

(def as-unqualified-lower-maps rs/as-unqualified-lower-maps)
(def as-unqualified-kebab-maps rs/as-unqualified-kebab-maps)

(def update-count :next.jdbc/update-count)

(defn get-connection
  "Get a JDBC connection from a connectable (datasource, db-spec,
  etc.). Returns a java.sql.Connection suitable for use with
  with-open."
  [connectable]
  (jdbc/get-connection connectable))

(defn get-datasource
  "Get a JDBC datasource from a datasource config map."
  [datasource-config]
  (try-nom :db/datasource
           "Failed to get datasource"
           (jdbc/get-datasource datasource-config)))

(defn execute-one!
  "Execute a SQL statement and return a single result.
  Wraps next.jdbc/execute-one! with error handling."
  ([datasource sql-params]
   (try-nom :db/execute-one
            "Failed to execute SQL statement"
            (jdbc/execute-one! datasource sql-params)))
  ([datasource sql-params opts]
   (try-nom :db/execute-one
            "Failed to execute SQL statement"
            (jdbc/execute-one! datasource sql-params opts))))

(defn execute!
  "Execute a SQL statement and return all results.
  Wraps next.jdbc/execute! with error handling."
  ([datasource sql-params]
   (try-nom :db/execute
            "Failed to execute SQL statement"
            (jdbc/execute! datasource sql-params)))
  ([datasource sql-params opts]
   (try-nom :db/execute
            "Failed to execute SQL statement"
            (jdbc/execute! datasource sql-params opts))))

(defn unique-violation?
  "Returns true if the anomaly represents a unique constraint violation."
  [anomaly]
  (when (error/anomaly? anomaly)
    (let [ex (:exception (error/payload anomaly))]
      (and (instance? PSQLException ex) (= "23505" (.getSQLState ex))))))
