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
  (-> m
      (update :created-at millis->iso)
      (update :updated-at millis->iso)))

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
         {:keys [name]} (get-in request [:parameters :body])
         result (organizations/new-organization
                 {:record-db record-db :record-store record-store}
                 name)]
     (if (error/anomaly? result)
       {:status 500 :body (error-response 500 result)}
       (let [api-key (:api-key result)]
         {:status 201
          :body {:organization (format-timestamps (:organization
                                                   result))
                 :api-key {:id (:id api-key)
                           :key-prefix (:key-prefix api-key)
                           :name (:name api-key)
                           :raw-key (:raw-key result)
                           :created-at (millis->iso (:created-at
                                                     api-key))}}})))))
