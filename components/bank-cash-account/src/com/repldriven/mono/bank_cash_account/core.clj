(ns com.repldriven.mono.bank-cash-account.core
  (:require
    [com.repldriven.mono.bank-cash-account.commands :as commands]

    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.processor.interface :as processor]))

(defn new-account
  "Opens a cash account with balances. Returns account map
  or anomaly."
  [config data]
  (let-nom> [account (commands/open-account config data)]
    (schema/pb->CashAccount account)))

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        {:keys [schemas]} config
        schema (get schemas command)]
    (if-not schema
      (error/fail :cash-account/process-command
                  {:message "No schema found for command" :command command})
      (let-nom> [data (avro/deserialize-same schema payload)]
        (case command
          "open-cash-account" (commands/new-account config data)
          "close-cash-account" (commands/close config data)
          "get-cash-account" (commands/get config data)
          (error/reject :cash-account/unknown-command
                        (str "Unknown command: " command)))))))

(defrecord CashAccountProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
