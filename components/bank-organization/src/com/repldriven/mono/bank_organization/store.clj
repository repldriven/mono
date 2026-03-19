(ns com.repldriven.mono.bank-organization.store
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.bank-schema.interface :as schema]))

(defn create
  "Persists an organization and its initial API key
  atomically. Returns nil or anomaly."
  [{:keys [record-db record-store]} org api-key]
  (error/try-nom
   :bank-organization/create
   "Failed to create organization"
   (fdb/transact-multi
    record-db
    record-store
    (fn [open-store]
      (let [org-store (open-store "organizations")
            key-store (open-store "api-keys")]
        (fdb/save-record org-store (schema/Organization->java org))
        (fdb/save-record key-store (schema/ApiKey->java api-key)))))))

(defn get-organizations
  "Lists organizations. Returns a sequence of organization
  maps or anomaly."
  [{:keys [record-db record-store]}]
  (error/try-nom :bank-organization/list
                 "Failed to list organizations"
                 (fdb/transact record-db
                               record-store
                               "organizations"
                               (fn [store]
                                 (mapv schema/pb->Organization
                                       (:records
                                        (fdb/scan-records store
                                                          {:limit 100})))))))
