(ns com.repldriven.mono.bank-api.party.commands
  (:require
    [com.repldriven.mono.bank-api.commands :as commands])
  (:import
    (java.time Instant)))

(defn- millis->iso [ms] (when (pos? ms) (str (Instant/ofEpochMilli ms))))

(defn- format-timestamps
  [party]
  (-> party
      (update :created-at millis->iso)
      (update :updated-at millis->iso)))

(defn- format-party-response
  [{:keys [status body] :as response}]
  (if (= 200 status)
    (assoc response :body (format-timestamps body))
    response))

(defn- dispatcher [request] (get-in request [:dispatchers :parties]))

(defn create-party
  [request]
  (format-party-response (commands/send
                          (dispatcher request)
                          request
                          "create-party"
                          "party"
                          (assoc (get-in request [:parameters :body])
                                 :organization-id
                                 (get-in request [:auth :organization-id])))))
