(ns com.repldriven.mono.bank-api.organization.handlers
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

(defn create-organization
  [request]
  (cond
   (nil? (:auth request))
   (let [result (error/unauthorized
                 :auth/unauthenticated
                 "Missing or invalid API key")]
     {:status 401 :body (error-response 401 result)})
   (not= :admin (get-in request [:auth :role]))
   (let
     [result
      (error/unauthorized
       :auth/forbidden
       "API key does not have sufficient privileges for this operation")]
     {:status 403 :body (error-response 403 result)})
   :else
   (let [{:keys [record-db record-store]} request
         {:keys [name currencies]} (get-in request
                                           [:parameters :body])
         config {:record-db record-db :record-store record-store}
         result (organizations/new-organization
                 config
                 name
                 :organisation-type-customer
                 currencies)]
     (if (error/anomaly? result)
       {:status 500 :body (error-response 500 result)}
       {:status 201
        :body (-> (:organization result)
                  format-rich-organization
                  (assoc :api-key-secret
                         (:raw-key result)))}))))
