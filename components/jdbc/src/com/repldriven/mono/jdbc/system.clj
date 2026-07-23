(ns com.repldriven.mono.jdbc.system
  (:require
    [com.repldriven.mono.system.interface :as system]

    [next.jdbc :as jdbc]))

(def datasources
  {:system/start
   (fn [{:system/keys [config]}]
     (reduce-kv (fn [m k v] (assoc m k (jdbc/get-datasource v))) {} config))
   :system/config system/required-component
   :system/instance-schema map?})

;; The singular form deliberately hands back the config map rather than a
;; javax.sql.DataSource: next.jdbc accepts a db-spec anywhere a connectable is
;; wanted, and keeping it as data means a system can be inspected without
;; holding a live handle.
(def datasource
  {:system/start (fn [{:system/keys [config]}] config)
   :system/config system/required-component
   :system/instance-schema some?})

(system/defcomponents :jdbc {:datasources datasources :datasource datasource})
