(ns com.repldriven.mono.service.pet-processor
  (:require
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.utility.interface :as util]))

(defn- ->response
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      {:status "ACCEPTED"
       :payload (avro/serialize (schemas "pet")
                                result)})))

;; An atom, because what this fixture exercises is the command path —
;; dispatcher to processor to reply — and a real store would only add
;; a container to it.
(defn- create
  [pets data]
  (let [{:keys [name species age-months]} data
        pet {:pet-id (str (util/uuidv7))
             :name name
             :species species
             :age-months age-months}]
    (swap! pets assoc (:pet-id pet) pet)
    pet))

(defn- dispatch
  [config pets message]
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
          "create-pet" (->response config (create pets data))
          (error/reject :pets/unknown-command
                        (str "Unknown command: " command)))))))

(defrecord PetProcessor [config pets]
  processor/Processor
    (process [_ message] (dispatch config pets message)))
