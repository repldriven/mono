(ns com.repldriven.mono.avro.serde
  (:require
    [com.repldriven.mono.error.interface :refer [try-nom]]
    [deercreeklabs.lancaster :as avro]))

(defn json->schema
  [json]
  (try-nom :avro/json->schema
           "Failed to parse Avro schema from JSON"
           (avro/json->schema json)))

(defn serialize
  [schema data]
  (try-nom :avro/serialize
           "Failed to serialize data to Avro"
           (avro/serialize schema data)))

(defn deserialize-same
  [schema data]
  (try-nom :avro/deserialize-same
           "Failed to deserialize Avro data"
           (avro/deserialize-same schema data)))

