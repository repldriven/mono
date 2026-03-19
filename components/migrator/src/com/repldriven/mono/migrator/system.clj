(ns com.repldriven.mono.migrator.system
  (:require
    [com.repldriven.mono.migrator.liquibase :as liquibase]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]))

(def migrations
  {:system/start (fn [{:system/keys [config]}]
                   (let [{:keys [datasource changelogs]} config]
                     (doseq [changelog changelogs]
                       (let [result (liquibase/migrate datasource changelog)]
                         (when (error/anomaly? result)
                           (throw (ex-info "Migration failed"
                                           {:changelog changelog
                                            :anomaly result})))))
                     datasource))
   :system/config {:datasource system/required-component
                   :changelogs system/required-component}
   :system/config-schema [:map [:datasource some?] [:changelogs some?]]
   :system/instance-schema some?})

(system/defcomponents :migrator {:migrations migrations})
