(ns com.repldriven.mono.kafka.kafka.serde
  "Avro serialisation for Kafka values, with one adjustment.

  Lancaster represents an Avro enum as a lower-cased keyword — `:accepted`
  for the symbol `ACCEPTED` — while the rest of the workspace passes these
  around as the strings the schema declares. `command`'s response envelope
  carries `:status \"ACCEPTED\"`, and pulsar accepts it because its
  GenericRecord layer converts strings to enum symbols on the way past.

  This does the same for lancaster, so a process-fn written for one transport
  works unchanged on the other. The bytes are identical either way: Avro
  encodes an enum as the index of its symbol, so only the in-memory
  representation differs."
  (:require
    [com.repldriven.mono.avro.interface :as avro]

    [clojure.string :as str]))

(defn- enum-field?
  "True when a field's schema is an enum, directly or as a branch of a union
  — `[:null {:type :enum …}]` is how an optional enum arrives."
  [field-type]
  (boolean (or (and (map? field-type) (= :enum (:type field-type)))
               (and (sequential? field-type)
                    (some #(enum-field? %)
                          field-type)))))

(def ^:private enum-fields
  "Field names of `schema` that hold enums. Memoised: the answer depends only
  on the schema, and this runs once per message otherwise."
  (memoize (fn [schema]
             (let [edn (avro/edn-schema schema)]
               (if-not (map? edn)
                 #{}
                 (into #{}
                       (keep (fn [{:keys [name type]}]
                               (when (enum-field? type) name))
                             (:fields edn))))))))

(defn- ->lancaster
  [schema data]
  (if-not (map? data)
    data
    (reduce (fn [m k]
              (let [v (get m k)]
                (if (string? v) (assoc m k (keyword (str/lower-case v))) m)))
            data
            (enum-fields schema))))

(defn- ->workspace
  [schema data]
  (if-not (map? data)
    data
    (reduce (fn [m k]
              (let [v (get m k)]
                (if (keyword? v) (assoc m k (str/upper-case (name v))) m)))
            data
            (enum-fields schema))))

(defn serialize
  [schema data]
  (avro/serialize schema (->lancaster schema data)))

(defn deserialize
  [schema bytes]
  (let [result (avro/deserialize-same schema bytes)]
    (if (map? result) (->workspace schema result) result)))
