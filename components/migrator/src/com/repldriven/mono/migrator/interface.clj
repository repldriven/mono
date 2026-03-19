(ns com.repldriven.mono.migrator.interface
  (:require
    com.repldriven.mono.migrator.system
    [com.repldriven.mono.migrator.liquibase :as liquibase]))

(defn migrate [db-spec resource-path] (liquibase/migrate db-spec resource-path))
