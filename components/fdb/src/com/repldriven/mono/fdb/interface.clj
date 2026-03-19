(ns com.repldriven.mono.fdb.interface
  (:require
    com.repldriven.mono.fdb.system.core
    [com.repldriven.mono.fdb.changelog :as changelog]
    [com.repldriven.mono.fdb.counter :as counter]
    [com.repldriven.mono.fdb.kv :as kv]
    [com.repldriven.mono.fdb.record :as record]))

;; KV layer

(defn set-str [db key value] (kv/set-str db key value))

(defn get-str [db key] (kv/get-str db key))

(defn set-bytes [db key value] (kv/set-bytes db key value))

(defn get-bytes [db key] (kv/get-bytes db key))

;; Record layer

(defn load-record
  [store & primary-key-parts]
  (apply record/load store primary-key-parts))

(defn save-record [store record] (record/save store record))

(defn load-records
  [store record-type field value]
  (record/query store record-type field value))

(defn query-records
  [store record-type field value]
  (record/query store record-type field value))

(defn scan-records [store opts] (record/scan store opts))

(defn write-changelog
  [store store-name record-id changelog-bytes]
  (changelog/write store store-name record-id changelog-bytes))

(defn process-changelog
  ([record-db consumer-id store-name handler]
   (changelog/process record-db consumer-id store-name handler))
  ([record-db consumer-id store-name handler opts]
   (changelog/process record-db consumer-id store-name handler opts)))

(defn allocate-counter
  [store & key-parts]
  (apply counter/allocate store key-parts))

(defn transact
  ([record-db open-store-fn store-name f]
   (record/transact record-db open-store-fn store-name f))
  ([record-db open-store-fn store-name f category message]
   (record/transact record-db open-store-fn store-name f category message)))

(defn transact-multi
  "Runs f within a single FDB transaction, passing a function
  that opens stores by name. All writes across stores are
  atomic."
  ([record-db open-store-fn f]
   (record/transact-multi record-db open-store-fn f))
  ([record-db open-store-fn f category message]
   (record/transact-multi record-db open-store-fn f category message)))
