(ns com.repldriven.mono.bank-api.simulate.handlers
  (:require
    [com.repldriven.mono.bank-api.commands :as commands]
    [com.repldriven.mono.bank-api.errors :refer [error-response]]

    [com.repldriven.mono.error.interface :as error]))

(defn- dispatcher
  [request]
  (get-in request [:dispatchers :transactions]))

(defn- interest-dispatcher
  [request]
  (get-in request [:dispatchers :interest]))

(defn- require-admin
  [request f]
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
   (f request)))

(defn inbound-transfer
  [request]
  (require-admin
   request
   (fn [{:keys [bootstrap] :as request}]
     (let [{:keys [account-id amount currency]}
           (get-in request [:parameters :body])
           internal-account-id (:account-id bootstrap)]
       (commands/send
        (dispatcher request)
        request
        "record-transaction"
        "transaction"
        {:idempotency-key (get-in request
                                  [:headers
                                   "idempotency-key"])
         :transaction-type
         :transaction-type-internal-transfer
         :currency currency
         :reference "Simulated inbound transfer"
         :legs [{:account-id internal-account-id
                 :balance-type :balance-type-suspense
                 :balance-status :balance-status-posted
                 :side :leg-side-debit
                 :amount amount}
                {:account-id account-id
                 :balance-type :balance-type-default
                 :balance-status :balance-status-posted
                 :side :leg-side-credit
                 :amount amount}]})))))

(defn accrue
  [request]
  (require-admin
   request
   (fn [request]
     (let [{:keys [org-id]} (get-in request
                                    [:parameters :path])
           {:keys [as-of-date]} (get-in request
                                        [:parameters :body])]
       (commands/send
        (interest-dispatcher request)
        request
        "accrue-daily-interest"
        "interest-result"
        {:idempotency-key (get-in request
                                  [:headers
                                   "idempotency-key"])
         :organization-id org-id
         :as-of-date as-of-date})))))

(defn capitalize
  [request]
  (require-admin
   request
   (fn [request]
     (let [{:keys [org-id]} (get-in request
                                    [:parameters :path])
           {:keys [as-of-date]} (get-in request
                                        [:parameters :body])]
       (commands/send
        (interest-dispatcher request)
        request
        "capitalize-monthly-interest"
        "interest-result"
        {:idempotency-key (get-in request
                                  [:headers
                                   "idempotency-key"])
         :organization-id org-id
         :as-of-date as-of-date})))))
