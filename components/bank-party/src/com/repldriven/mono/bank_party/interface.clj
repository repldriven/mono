(ns com.repldriven.mono.bank-party.interface
  (:require
    com.repldriven.mono.bank-party.system

    [com.repldriven.mono.bank-party.commands :as commands]
    [com.repldriven.mono.bank-party.store :as store]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.bank-schema.interface :as schema]))

(defn new-party
  "Creates a party. Returns party map or anomaly."
  [config data]
  (let-nom> [pb (commands/new-party config data)]
    (schema/pb->Party pb)))

(defn get-parties
  "Lists parties for an organization. Returns sequence of
  party maps or anomaly."
  [config org-id]
  (store/get-parties config org-id))
