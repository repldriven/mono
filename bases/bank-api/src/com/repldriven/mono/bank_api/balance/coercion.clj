(ns com.repldriven.mono.bank-api.balance.coercion)

(def ^:private balance-types
  {"default" :balance-type-default
   "interest-accrued" :balance-type-interest-accrued
   "interest-paid" :balance-type-interest-paid
   "purchase" :balance-type-purchase
   "cash" :balance-type-cash})

(def ^:private balance-statuses
  {"posted" :balance-status-posted
   "pending-incoming" :balance-status-pending-incoming
   "pending-outgoing" :balance-status-pending-outgoing})

(def decode-balance-type
  (let [m (merge balance-types
                 (zipmap (map keyword (keys balance-types))
                         (vals balance-types)))]
    (fn [v] (get m v v))))

(def encode-balance-type
  (let [m (assoc (zipmap (vals balance-types)
                         (map keyword (keys balance-types)))
                 :balance-type-unknown
                 :unknown)]
    (fn [v] (get m v v))))

(def balance-type-json-schema {:type "string" :enum (vec (keys balance-types))})

(def decode-balance-status
  (let [m (merge balance-statuses
                 (zipmap (map keyword (keys balance-statuses))
                         (vals balance-statuses)))]
    (fn [v] (get m v v))))

(def encode-balance-status
  (let [m (assoc (zipmap (vals balance-statuses)
                         (map keyword (keys balance-statuses)))
                 :balance-status-unknown
                 :unknown)]
    (fn [v] (get m v v))))

(def balance-status-json-schema
  {:type "string" :enum (vec (keys balance-statuses))})
