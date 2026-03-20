(ns com.repldriven.mono.bank-api.party.coercion)

(def ^:private party-types
  {"person" :party-type-person
   "internal" :party-type-internal
   "organization" :party-type-organization})

(def ^:private party-statuses
  {"pending" :party-status-pending
   "active" :party-status-active
   "suspended" :party-status-suspended
   "closed" :party-status-closed})

(def ^:private identifier-types
  {"national-insurance" :identifier-type-national-insurance
   "passport" :identifier-type-passport
   "driving-licence" :identifier-type-driving-licence
   "national-id-card" :identifier-type-national-id-card
   "tax-id" :identifier-type-tax-id})

(def decode-party-type
  (let [m (merge party-types
                 (zipmap (map keyword (keys party-types)) (vals party-types)))]
    (fn [v] (get m v v))))

(def encode-party-type
  (let [m (assoc (zipmap (vals party-types) (map keyword (keys party-types)))
                 :party-type-unknown
                 :unknown)]
    (fn [v] (get m v v))))

(def party-type-json-schema {:type "string" :enum (vec (keys party-types))})

(def decode-party-status
  (let [m (merge party-statuses
                 (zipmap (map keyword (keys party-statuses))
                         (vals party-statuses)))]
    (fn [v] (get m v v))))

(def encode-party-status
  (let [m (assoc (zipmap (vals party-statuses)
                         (map keyword (keys party-statuses)))
                 :party-status-unknown
                 :unknown)]
    (fn [v] (get m v v))))

(def party-status-json-schema
  {:type "string" :enum (vec (keys party-statuses))})

(def decode-identifier-type
  (let [m (merge identifier-types
                 (zipmap (map keyword (keys identifier-types))
                         (vals identifier-types)))]
    (fn [v] (get m v v))))

(def encode-identifier-type
  (let [m (assoc (zipmap (vals identifier-types)
                         (map keyword (keys identifier-types)))
                 :identifier-type-unknown
                 :unknown)]
    (fn [v] (get m v v))))

(def identifier-type-json-schema
  {:type "string" :enum (vec (keys identifier-types))})
