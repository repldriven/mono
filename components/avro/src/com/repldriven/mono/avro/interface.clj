(ns com.repldriven.mono.avro.interface
  (:require
    com.repldriven.mono.avro.system
    [com.repldriven.mono.avro.serde :as serde]))

(defn json->schema [json] (serde/json->schema json))

(defn serialize [schema data] (serde/serialize schema data))

(defn deserialize-same [schema data] (serde/deserialize-same schema data))
