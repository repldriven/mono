(ns com.repldriven.mono.bank-api.organization.coercion)

(def ^:private organisation-types
  {"internal" :organisation-type-internal
   "customer" :organisation-type-customer})

(def decode-organisation-type
  (let [m (merge organisation-types
                 (zipmap (map keyword (keys organisation-types))
                         (vals organisation-types)))]
    (fn [v] (get m v v))))

(def encode-organisation-type
  (let [m (assoc (zipmap (vals organisation-types)
                         (map keyword (keys organisation-types)))
                 :organisation-type-unknown
                 :unknown)]
    (fn [v] (get m v v))))

(def organisation-type-json-schema
  {:type "string" :enum (vec (keys organisation-types))})
