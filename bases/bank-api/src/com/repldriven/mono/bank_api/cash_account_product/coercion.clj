(ns com.repldriven.mono.bank-api.cash-account-product.coercion)

(def ^:private account-types
  {"current" :account-type-current
   "savings" :account-type-savings
   "term-deposit" :account-type-term-deposit})

(def ^:private balance-sheet-sides
  {"asset" :balance-sheet-side-asset "liability" :balance-sheet-side-liability})

(def decode-account-type
  (let [m (merge account-types
                 (zipmap (map keyword (keys account-types))
                         (vals account-types)))]
    (fn [v] (get m v v))))

(def encode-account-type
  (let [m (assoc (zipmap (vals account-types)
                         (map keyword (keys account-types)))
                 :account-type-unknown
                 :unknown)]
    (fn [v] (get m v v))))

(def account-type-json-schema {:type "string" :enum (vec (keys account-types))})

(def decode-balance-sheet-side
  (let [m (merge balance-sheet-sides
                 (zipmap (map keyword (keys balance-sheet-sides))
                         (vals balance-sheet-sides)))]
    (fn [v] (get m v v))))

(def encode-balance-sheet-side
  (let [m (assoc (zipmap (vals balance-sheet-sides)
                         (map keyword (keys balance-sheet-sides)))
                 :balance-sheet-side-unknown
                 :unknown)]
    (fn [v] (get m v v))))

(def balance-sheet-side-json-schema
  {:type "string" :enum (vec (keys balance-sheet-sides))})
