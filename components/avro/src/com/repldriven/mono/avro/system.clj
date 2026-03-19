(ns com.repldriven.mono.avro.system
  (:require
    [com.repldriven.mono.avro.serde :as serde]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]
    [clojure.java.io :as io]))

(def serde-component
  {:system/start (fn [{:system/keys [config]}]
                   (reduce-kv
                    (fn [m k path]
                      (let [json (slurp (io/resource path))
                            schema (serde/json->schema json)]
                        (if (error/anomaly? schema)
                          (throw (ex-info "Failed to load Avro schema"
                                          {:name k :path path :anomaly schema}))
                          (assoc m (name k) schema))))
                    {}
                    (:schemas config)))
   :system/config {:schemas system/required-component}
   :system/config-schema [:map [:schemas map?]]
   :system/instance-schema map?})

(system/defcomponents :avro {:serde serde-component})
