(ns com.repldriven.mono.bank-api-key.store
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.bank-schema.interface :as schema]))

(defn get-api-key
  "Looks up an API key by its hash. Returns the ApiKey map
  or nil."
  [{:keys [record-db record-store]} key-hash]
  (fdb/transact
   record-db
   record-store
   "api-keys"
   (fn [store]
     (first (map schema/pb->ApiKey
                 (fdb/query-records store "ApiKey" "key_hash" key-hash))))))

(defn get-api-keys
  "Lists all API keys for a given organization. Returns a
  sequence of ApiKey maps."
  [{:keys [record-db record-store]} org-id]
  (error/try-nom
   :bank-api-key/list
   "Failed to list API keys"
   (fdb/transact
    record-db
    record-store
    "api-keys"
    (fn [store]
      (mapv schema/pb->ApiKey
            (fdb/query-records store "ApiKey" "organization_id" org-id))))))
