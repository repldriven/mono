(ns com.repldriven.mono.bank-api.cash-account.coercion)

(def ^:private cash-account-statuses
  {"opening" :cash-account-status-opening
   "opened" :cash-account-status-opened
   "closing" :cash-account-status-closing
   "closed" :cash-account-status-closed})

(def decode-cash-account-status
  (let [m (merge cash-account-statuses
                 (zipmap (map keyword (keys cash-account-statuses))
                         (vals cash-account-statuses)))]
    (fn [v] (get m v v))))

(def encode-cash-account-status
  (let [m (zipmap (vals cash-account-statuses)
                  (map keyword (keys cash-account-statuses)))]
    (fn [v] (get m v v))))

(def cash-account-status-json-schema
  {:type "string" :enum (vec (keys cash-account-statuses))})
