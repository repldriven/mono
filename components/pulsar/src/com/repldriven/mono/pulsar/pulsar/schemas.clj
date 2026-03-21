(ns com.repldriven.mono.pulsar.pulsar.schemas
  (:refer-clojure :exclude [name namespace resolve type])
  (:require
    [clojure.data.json :as json]
    [clojure.java.data :as j]
    [clojure.java.io :as io]
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [try-nom]]
    [com.repldriven.mono.log.interface :as log])
  (:import
    (java.util Map)
    (org.apache.pulsar.client.api Schema)
    (org.apache.pulsar.client.api.schema SchemaDefinition)
    (org.apache.pulsar.common.protocol.schema PostSchemaPayload)
    (org.apache.pulsar.common.schema SchemaInfo)))

(defn- create-payload
  ^PostSchemaPayload [type schema properties]
  (PostSchemaPayload. (or type "")
                      (if (some? schema) (json/write-str schema) "")
                      (or properties {})))

(defn- create-definition
  ^SchemaDefinition [schema properties]
  (.. (SchemaDefinition/builder)
      (withJsonDef (json/write-str schema))
      (withProperties (j/to-java Map (or properties {})))
      build))

(defn- create-schema
  ^Schema
  ([type]
   (case type
     ;; primitive
     "BOOL" Schema/BOOL
     "BYTEBUFFER" Schema/BYTEBUFFER
     "BYTES" Schema/BYTES
     "DATE" Schema/DATE
     "DOUBLE" Schema/DOUBLE
     "FLOAT" Schema/FLOAT
     "INSTANT" Schema/INSTANT
     "INT16" Schema/INT16
     "INT32" Schema/INT32
     "INT64" Schema/INT64
     "INT8" Schema/INT8
     "LOCAL_DATE" Schema/LOCAL_DATE
     "LOCAL_DATE_TIME" Schema/LOCAL_DATE_TIME
     "STRING" Schema/STRING
     "TIME" Schema/TIME
     "TIMESTAMP" Schema/TIMESTAMP
     ;; auto
     "AUTO_CONSUME" (Schema/AUTO_CONSUME)
     "AUTO_PRODUCE_BYTES" (Schema/AUTO_PRODUCE_BYTES)))
  ([type schema properties]
   (let [schema-definition (create-definition schema properties)]
     (case type
       ;; complex
       "JSON" (Schema/JSON schema-definition)
       "AVRO" (Schema/AVRO schema-definition)
       (create-schema type)))))

(defn- load-avsc
  [filename]
  (log/info "Loading Pulsar schema file:" filename)
  (-> filename
      io/resource
      io/file
      slurp
      json/read-str))

(defn- read-schema
  [file-or-ref]
  (if (string? file-or-ref) (load-avsc file-or-ref) file-or-ref))

(defn- schema->avro
  [^Schema schema]
  (let [^SchemaInfo schema-info (.getSchemaInfo schema)]
    (-> schema-info
        .toString
        json/read-str
        (get "schema")
        json/write-str
        avro/json->schema)))

(defn- create-schema-entry
  [type schema properties]
  (let [pulsar-schema (create-schema type schema properties)]
    (cond-> {:payload (create-payload type schema properties)
             :schema pulsar-schema}
            (contains? #{"AVRO" "JSON"} type)
            (assoc :avro
                   (schema->avro pulsar-schema)))))

(defn create-schemas
  [coll]
  (log/info "Creating Pulsar schemas:" (keys coll))
  (try-nom :pulsar/schemas-create
           "Failed to create Pulsar schemas"
           (into {}
                 (doall (reduce-kv
                         (fn [m k {:keys [type schema properties]}]
                           (assoc m
                                  k
                                  (create-schema-entry type
                                                       (read-schema
                                                        schema)
                                                       properties)))
                         {}
                         coll)))))

(defn resolve
  [schemas s]
  (cond (keyword? s)
        (get-in schemas [s :schema])
        (map? s)
        (try-nom :pulsar/schema-resolve
                 "Failed to resolve Pulsar schema"
                 (let [{:keys [type schema properties]} s]
                   (create-schema type schema properties)))
        :else
        s))

(defn resolve-payload
  [schemas s]
  (cond (keyword? s)
        (get-in schemas [s :payload])
        (map? s)
        (try-nom :pulsar/schema-resolve-payload
                 "Failed to resolve Pulsar schema payload"
                 (let [{:keys [type schema properties]} s]
                   (create-payload type schema properties)))
        :else
        (error/fail :pulsar/schema-resolve-payload-invalid
                    (format "Invalid value for schema payload: %s" s))))

