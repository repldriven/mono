(ns com.repldriven.mono.bank-interest.core
  (:require
    [com.repldriven.mono.bank-interest.commands :as commands]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]
    [com.repldriven.mono.processor.interface :as processor]))

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        {:keys [schemas]} config
        schema (get schemas command)]
    (if-not schema
      (error/fail :interest/process-command
                  {:message "No schema found for command"
                   :command command})
      (let-nom> [data (avro/deserialize-same schema
                                             payload)]
        (case command
          "accrue-daily-interest"
          (commands/accrue-daily config data)

          "capitalize-monthly-interest"
          (commands/capitalize-monthly config data)

          (error/reject :interest/unknown-command
                        (str "Unknown command: "
                             command)))))))

(defrecord InterestProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
