(ns com.repldriven.mono.bank-party.interface
  (:require
    com.repldriven.mono.bank-party.system

    [com.repldriven.mono.bank-party.commands :as commands]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.bank-schema.interface :as schema]))

(defn create
  "Creates a party. Returns party map or anomaly."
  [config data]
  (error/let-nom> [pb (commands/create config data)]
    (schema/pb->Party pb)))
