(ns com.repldriven.mono.bank-party.core
  (:require
    [com.repldriven.mono.bank-party.commands :as commands]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]))

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        {:keys [schemas]} config
        schema (get schemas command)]
    (if-not schema
      (error/fail :bank-party/process-command
                  {:message "No schema found for command" :command command})
      (error/let-nom> [data (avro/deserialize-same schema payload)]
        (case command
          "create-party" (commands/create-party config data)
          (error/reject :bank-party/unknown-command
                        (str "Unknown command: " command)))))))

(defrecord PartyProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
