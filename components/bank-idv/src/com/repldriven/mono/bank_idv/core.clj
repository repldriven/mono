(ns com.repldriven.mono.bank-idv.core
  (:require
    [com.repldriven.mono.bank-idv.commands :as commands]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.processor.interface :as processor]))

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        {:keys [schemas]} config
        schema (get schemas command)]
    (if-not schema
      (error/fail :idv/process-command
                  {:message "No schema found for command" :command command})
      (let-nom> [data (avro/deserialize-same schema payload)]
        (case command
          "initiate-idv" (commands/initiate config data)
          "get-idv" (commands/get config data)
          (error/reject :idv/unknown-command
                        (str "Unknown command: " command)))))))

(defrecord IdvProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
