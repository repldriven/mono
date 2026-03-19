(ns com.repldriven.mono.bank-idv.commands
  (:refer-clojure :exclude [get read])
  (:require
    [com.repldriven.mono.bank-idv.domain :as domain]
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.bank-schema.interface :as schema]))

(defn- idv-pb->avro
  "Converts protobuf Idv to Avro-compatible map. Proto
  optional int64 defaults to 0; Avro nullable expects nil."
  [pb]
  (let [idv (schema/pb->Idv pb)] (update idv :completed-at #(when (pos? %) %))))

(defn- save
  "Saves IDV to store, writes changelog entry with
  serialized changelog proto, returns protobuf record or
  anomaly."
  [store idv changelog]
  (error/let-nom> [_ (fdb/save-record store (schema/Idv->java idv))
                   _
                   (fdb/write-changelog store
                                        "idvs"
                                        (:verification-id idv)
                                        (schema/IdvChangelog->pb
                                         (assoc changelog
                                                :organization-id
                                                (:organization-id
                                                 idv))))]
    (schema/Idv->pb idv)))

(defn- create
  "Creates a new IDV in a transaction. Returns protobuf
  record or anomaly."
  [config data]
  (let [{:keys [record-db record-store]} config]
    (fdb/transact record-db
                  record-store
                  "idvs"
                  (fn [store]
                    (error/let-nom> [idv (domain/new-idv data)]
                      (save store
                            idv
                            {:verification-id (:verification-id
                                               idv)
                             :status-after (:status idv)}))))))

(defn- ->response
  "Converts a protobuf record to an ACCEPTED response.
  Returns anomalies unchanged for the processor to handle."
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      {:status "ACCEPTED"
       :payload (avro/serialize (schemas "idv") (idv-pb->avro result))})))

(defn- read
  "Loads IDV by id. Returns protobuf record or anomaly."
  [config organization-id verification-id]
  (let [{:keys [record-db record-store]} config]
    (fdb/transact record-db
                  record-store
                  "idvs"
                  (fn [store]
                    (or (fdb/load-record store organization-id verification-id)
                        (error/reject :bank-idv/not-found "IDV not found"))))))

(defn initiate
  "Initiates a new IDV."
  [config data]
  (->response config (create config data)))

(defn get
  "Returns the current IDV or rejection anomaly."
  [config data]
  (let [{:keys [organization-id verification-id]} data]
    (->response config (read config organization-id verification-id))))
