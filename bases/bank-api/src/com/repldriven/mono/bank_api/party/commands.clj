(ns com.repldriven.mono.bank-api.party.commands
  (:require
    [com.repldriven.mono.bank-api.commands :as commands]))

(defn- dispatcher [request] (get-in request [:dispatchers :parties]))

(defn create-party
  [request]
  (commands/send (dispatcher request)
                 request
                 "create-party"
                 "party"
                 (assoc (get-in request [:parameters :body])
                        :organization-id
                        (get-in request [:auth :organization-id]))))
