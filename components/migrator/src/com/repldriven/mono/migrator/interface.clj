(ns com.repldriven.mono.migrator.interface
  "Liquibase-driven schema migrations. Registers a `:migrator/migrations`
  donut.system component that runs each configured changelog at start-up,
  throwing on the first anomaly so the system fails fast."
  (:require
    com.repldriven.mono.migrator.system
    [com.repldriven.mono.migrator.liquibase :as liquibase]))

(defn migrate
  "Run a Liquibase changelog (a classpath resource path) against
  `db-spec`. Returns nil on success or a `:migrator/migration-failed`
  anomaly carrying the underlying exception.

  Args:
  - db-spec: a `db` brick datasource.
  - resource-path: classpath path to a Liquibase changelog file."
  [db-spec resource-path]
  (liquibase/migrate db-spec resource-path))
