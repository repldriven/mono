(ns com.repldriven.mono.avro.interface
  (:require
    com.repldriven.mono.avro.system
    [com.repldriven.mono.avro.serde :as serde]))

(defn json->schema [json] (serde/json->schema json))

(defn edn-schema
  "The schema as Clojure data — `{:type :record :fields [{:name … :type …}]}`
  — for callers that need to know its shape rather than just use it. Returns
  an anomaly if it cannot be read.

  Args:
  - schema: a compiled Avro schema."
  [schema]
  (serde/edn-schema schema))

(defn serialize [schema data] (serde/serialize schema data))

(defn deserialize-same [schema data] (serde/deserialize-same schema data))
