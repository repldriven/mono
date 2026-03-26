(ns com.repldriven.mono.bank-api.cash-account.queries
  (:require
    [com.repldriven.mono.bank-api.cursor :as cursor]
    [com.repldriven.mono.bank-api.errors :refer [error-response]]
    [com.repldriven.mono.bank-cash-account.interface :as
     cash-accounts]
    [com.repldriven.mono.bank-transaction.interface :as
     transactions]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.bank-schema.interface :as schema]))

(def ^:private default-page-size 20)
(def ^:private max-page-size 100)

(defn- parse-page-size
  [s]
  (let [n (when s
            (try (Integer/parseInt s)
                 (catch NumberFormatException _ nil)))]
    (cond (nil? n)
          default-page-size
          (< n 1)
          1
          (> n max-page-size)
          max-page-size
          :else
          n)))

(defn- build-links
  [base before-id after-id]
  (cond-> {}
          after-id
          (assoc :next
                 (str base
                      "?page[after]="
                      (cursor/encode after-id)))
          before-id
          (assoc :prev
                 (str base
                      "?page[before]="
                      (cursor/encode before-id)))))

(defn list-cash-accounts
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        query (get-in request [:parameters :query])
        after-id (cursor/decode
                  (get query (keyword "page[after]")))
        before-id (cursor/decode
                   (get query (keyword "page[before]")))
        size (parse-page-size
              (get query (keyword "page[size]")))
        result (cash-accounts/get-accounts
                {:record-db record-db
                 :record-store record-store}
                org-id
                (cond-> {:limit size}
                        after-id
                        (assoc :after after-id)
                        before-id
                        (assoc :before before-id)))]
    (if (error/anomaly? result)
      {:status 500 :body (error-response 500 result)}
      (let [{:keys [accounts before after]} result
            links (when (seq accounts)
                    (build-links "/v1/cash-accounts"
                                 (when after-id before)
                                 after))]
        {:status 200
         :body (cond-> {:cash-accounts accounts}
                       (seq links)
                       (assoc :links links))}))))

(defn get-cash-account
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        {:keys [account-id]} (get-in request [:parameters :path])
        result (fdb/transact record-db
                             record-store
                             "cash-accounts"
                             (fn [store]
                               (fdb/load-record store org-id account-id)))]
    (cond (error/anomaly? result)
          {:status 500
           :body (error-response 500 result)}
          (nil? result)
          {:status 404
           :body (error-response 404 "FAILED"
                                 "cash-accounts/not-found"
                                 "Cash account not found")}
          :else
          {:status 200
           :body (schema/pb->CashAccount result)})))

(defn list-transactions
  [request]
  (let [{:keys [record-db record-store]} request
        account-id (get-in request
                           [:parameters :path :account-id])
        result (transactions/get-account-transactions
                {:record-db record-db
                 :record-store record-store}
                account-id)]
    (if (error/anomaly? result)
      {:status 500 :body (error-response 500 result)}
      {:status 200 :body {:transactions result}})))
