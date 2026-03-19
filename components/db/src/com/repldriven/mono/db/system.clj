(ns com.repldriven.mono.db.system
  (:require
    [com.repldriven.mono.system.interface :as system]
    [next.jdbc]))

(def datasources
  {:system/start (fn [{:system/keys [config]}]
                   (reduce-kv (fn [m k v]
                                (assoc m k (next.jdbc/get-datasource v)))
                              {}
                              config))
   :system/config system/required-component
   :system/instance-schema map?})

(def datasource
  {:system/start (fn [{:system/keys [config]}] config)
   :system/config system/required-component
   :system/instance-schema some?})

(system/defcomponents :db {:datasources datasources :datasource datasource})
