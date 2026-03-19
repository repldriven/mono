(ns com.repldriven.mono.pulsar.pulsar.generic-record
  (:require
    [clojure.string :as str])
  (:import
    (org.apache.pulsar.client.api.schema GenericRecord)
    (org.apache.pulsar.shade.org.apache.avro Schema$Type)
    (org.apache.pulsar.shade.org.apache.avro.generic
     GenericData$EnumSymbol
     GenericData$Record)))

(defn- field-name->key
  "Convert an Avro field name to a kebab-case keyword."
  [s]
  (keyword (str/replace s \_ \-)))

(defn- key->field-name
  "Convert a Clojure key to an Avro field name by replacing
  hyphens with underscores."
  [k]
  (str/replace (name k) \- \_))

(defn- enum-schema
  "Return the Avro enum schema for a field schema, or nil.
  Handles both direct ENUM and UNION containing an ENUM."
  [field-schema]
  (let [t (.getType field-schema)]
    (cond (= t Schema$Type/ENUM)
          field-schema
          (= t Schema$Type/UNION)
          (some (fn [s] (when (= Schema$Type/ENUM (.getType s)) s))
                (.getTypes field-schema)))))

(defn serialize
  "Convert a Clojure map to an Avro GenericRecord using the
  provided Avro schema."
  [avro-schema data]
  (when (and avro-schema (map? data))
    (let [^GenericRecord record (GenericData$Record. avro-schema)]
      (doseq [[k v] data]
        (let [n (key->field-name k)
              f (.getField avro-schema n)
              es (when (and f (some? v)) (enum-schema (.schema f)))
              v (if es (GenericData$EnumSymbol. es (str v)) v)]
          (.put record n v)))
      record)))

(defn deserialize
  "Convert a Pulsar GenericRecord to a Clojure map with
  kebab-case keyword keys."
  [^GenericRecord record]
  (when record
    (let [fields (.getFields record)]
      (into {}
            (map (fn [field]
                   (let [field-name (.getName field)
                         value (.getField record field-name)]
                     [(field-name->key field-name)
                      (cond (instance? GenericRecord value)
                            (deserialize value)
                            (instance? GenericData$EnumSymbol value)
                            (str value)
                            (instance? java.nio.ByteBuffer value)
                            (let [^java.nio.ByteBuffer buf (.duplicate value)
                                  arr (byte-array (.remaining buf))]
                              (.get buf arr)
                              arr)
                            :else
                            value)])))
            fields))))
