(ns com.repldriven.mono.service.pet-processor
  (:require
    [com.repldriven.mono.test_schemas.pets :as pets]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.processor.interface :as processor]

    [protojure.protobuf :as proto])
  (:import
    (com.repldriven.mono.test_schemas.pets PetProto$Pet)))

(defn- Pet->java
  [m]
  (PetProto$Pet/parseFrom (proto/->pb (pets/new-Pet m))))

(defn- ->response
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      {:status "ACCEPTED"
       :payload (avro/serialize (schemas "pet")
                                result)})))

(defn- create
  [config data]
  (let [{:keys [name species age-months]} data
        pet-id (str (java.util.UUID/randomUUID))]
    (fdb/transact
     config
     (fn [txn]
       (let-nom>
         [_ (fdb/save-record (fdb/open txn "pets")
                             (Pet->java {:pet-id pet-id
                                         :name name
                                         :species species
                                         :age-months age-months}))]
         {:pet-id pet-id
          :name name
          :species species
          :age-months age-months})))))

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        {:keys [schemas]} config
        schema (get schemas command)]
    (if-not schema
      (error/fail :pets/process-command
                  {:message "No schema found for command"
                   :command command})
      (let-nom>
        [data (avro/deserialize-same schema payload)]
        (case command
          "create-pet" (->response config (create config data))
          (error/reject :pets/unknown-command
                        (str "Unknown command: " command)))))))

(defrecord PetProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
