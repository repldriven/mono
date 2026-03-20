(ns com.repldriven.mono.bank-api.organization.queries
  (:require
    [com.repldriven.mono.bank-api.errors :refer [error-response]]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.bank-organization.interface :as organizations])
  (:import
    (java.time Instant)))

(defn- millis->iso [ms] (when (pos? ms) (str (Instant/ofEpochMilli ms))))

(defn- format-timestamps
  [m]
  (cond-> m
          (:created-at m)
          (update :created-at millis->iso)
          (:updated-at m)
          (update :updated-at millis->iso)))

(defn- format-rich-organization
  "Formats timestamps on an organization and its nested
  party, accounts (with balances), and api-key."
  [org]
  (-> org
      format-timestamps
      (update :party format-timestamps)
      (update :accounts
              #(mapv (fn [a]
                       (-> a
                           format-timestamps
                           (update :balances
                                   (partial mapv
                                            format-timestamps))))
                     %))
      (update :api-key format-timestamps)))

(defn list-organizations
  [request]
  (let [config {:record-db (:record-db request)
                :record-store (:record-store request)}
        result (organizations/get-organizations config)]
    (if (error/anomaly? result)
      {:status 500 :body (error-response 500 result)}
      {:status 200
       :body {:organizations
              (mapv format-rich-organization result)}})))
