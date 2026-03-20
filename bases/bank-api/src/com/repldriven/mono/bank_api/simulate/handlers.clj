(ns com.repldriven.mono.bank-api.simulate.handlers
  (:require
    [com.repldriven.mono.bank-api.commands :as commands]
    [com.repldriven.mono.bank-api.errors :refer [error-response]]

    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.error.interface :as error])
  (:import
    (java.time Instant)))

(defn- millis->iso
  [ms]
  (when (pos? ms) (str (Instant/ofEpochMilli ms))))

(defn- format-timestamps
  [m]
  (cond-> m
          (:created-at m)
          (update :created-at millis->iso)
          (:updated-at m)
          (update :updated-at millis->iso)))

(defn- format-leg
  [leg]
  (cond-> leg
          (:created-at leg)
          (update :created-at millis->iso)))

(defn- format-transaction-response
  [{:keys [status body] :as response}]
  (if (= 200 status)
    (assoc response
           :body
           (-> body
               format-timestamps
               (update :legs (partial mapv format-leg))))
    response))

(defn- find-account
  "Finds the first account matching the given currency."
  [accounts currency]
  (first (filter #(= currency (:currency %)) accounts)))

(defn- dispatcher
  [request]
  (get-in request [:dispatchers :transactions]))

(defn inbound-transfer
  [request]
  (cond
   (nil? (:auth request))
   {:status 401
    :body (error-response
           401
           (error/unauthorized :auth/unauthenticated
                               "Missing or invalid API key"))}
   (not= :admin (get-in request [:auth :role]))
   {:status 403
    :body (error-response
           403
           (error/unauthorized
            :auth/forbidden
            "API key does not have sufficient privileges"))}
   :else
   (let [{:keys [record-db record-store bootstrap]} request
         {:keys [org-id]} (get-in request [:parameters :path])
         {:keys [amount currency]} (get-in request
                                           [:parameters :body])
         internal-account-id (:account-id bootstrap)
         config {:record-db record-db
                 :record-store record-store}
         accounts (cash-accounts/get-accounts config org-id)]
     (if (error/anomaly? accounts)
       {:status 500 :body (error-response 500 accounts)}
       (if-let [customer-account (find-account accounts
                                               currency)]
         (let [idempotency-key (get-in request
                                       [:headers
                                        "idempotency-key"])]
           (format-transaction-response
            (commands/send
             (dispatcher request)
             request
             "record-transaction"
             "transaction"
             {:idempotency-key idempotency-key
              :transaction-type
              :transaction-type-internal-transfer
              :currency currency
              :reference "Simulated inbound transfer"
              :legs [{:account-id internal-account-id
                      :balance-type :balance-type-suspense
                      :balance-status :balance-status-posted
                      :side :leg-side-debit
                      :amount amount}
                     {:account-id (:account-id
                                   customer-account)
                      :balance-type :balance-type-default
                      :balance-status :balance-status-posted
                      :side :leg-side-credit
                      :amount amount}]})))
         {:status 404
          :body (error-response
                 404
                 (error/fail
                  :simulate/no-account
                  {:message
                   (str "No account found for currency "
                        currency)}))})))))
