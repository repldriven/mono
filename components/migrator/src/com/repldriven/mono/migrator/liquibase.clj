(ns com.repldriven.mono.migrator.liquibase
  (:require
    [com.repldriven.mono.db.interface :as sql]
    [com.repldriven.mono.error.interface :as error]
    [clojure.java.io :as io])
  (:import
    (liquibase Contexts LabelExpression Liquibase)
    (liquibase.database Database DatabaseFactory)
    (liquibase.database.jvm JdbcConnection)
    (liquibase.resource DirectoryResourceAccessor ResourceAccessor)
    (java.io File)))

(defn- resource-accessor
  "Create a DirectoryResourceAccessor from a classpath resource path.
   Resolves the resource to its filesystem location."
  [resource-path]
  (let [resource (io/resource resource-path)
        file (io/file (.toURI resource))
        dir (.getParentFile file)]
    (DirectoryResourceAccessor. dir)))

(defn migrate
  [db-spec resource-path]
  (error/try-nom
   :migrator/migration-failed
   "Failed to run database migrations"
   (with-open [conn (sql/get-connection db-spec)]
     (let [jdbc-connection (JdbcConnection. conn)
           ^Database database (.findCorrectDatabaseImplementation
                               (DatabaseFactory/getInstance)
                               jdbc-connection)
           ^ResourceAccessor accessor (resource-accessor resource-path)
           ^String filename (.getName (File. ^String resource-path))
           lb (Liquibase. filename accessor database)]
       (.update lb (Contexts.) (LabelExpression.))))))
