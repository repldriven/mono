(ns com.repldriven.mono.bank-api.cash-account.queries
  (:require
    [com.repldriven.mono.bank-api.cursor :as cursor]
    [com.repldriven.mono.bank-api.errors :refer [error-response]]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.bank-schema.interface :as schema])
  (:import
    (java.time Instant)))

(defn- millis->iso [ms] (when (pos? ms) (str (Instant/ofEpochMilli ms))))

(defn- format-timestamps
  [account]
  (-> account
      (update :created-at millis->iso)
      (update :updated-at millis->iso)))

(def ^:private default-page-size 20)
(def ^:private max-page-size 100)

(defn- parse-page-size
  [s]
  (let [n (when s
            (try (Integer/parseInt s) (catch NumberFormatException _ nil)))]
    (cond (nil? n)
          default-page-size
          (< n 1)
          1
          (> n max-page-size)
          max-page-size
          :else
          n)))

(defn- build-links
  [{:keys [accounts has-more after before]}]
  (let [base "/v1/cash-accounts"
        first-id (:account-id (first accounts))
        last-id (:account-id (peek accounts))
        forward? (some? after)
        backward? (some? before)]
    (cond-> {}
            (or (and (not backward?) has-more) backward?)
            (assoc :next (str base "?page[after]=" (cursor/encode last-id)))
            (or forward? (and backward? has-more))
            (assoc :prev
                   (str base "?page[before]=" (cursor/encode first-id))))))

(defn list-cash-accounts
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        query (get-in request [:parameters :query])
        after-cursor (get query (keyword "page[after]"))
        before-cursor (get query (keyword "page[before]"))
        size (parse-page-size (get query (keyword "page[size]")))
        after-id (cursor/decode after-cursor)
        before-id (cursor/decode before-cursor)
        result (fdb/transact record-db
                             record-store
                             "cash-accounts"
                             (fn [store]
                               (fdb/scan-records store
                                                 {:prefix [org-id]
                                                  :after after-id
                                                  :before before-id
                                                  :limit size})))]
    (if (error/anomaly? result)
      {:status 500 :body (error-response 500 result)}
      (let [accounts (mapv (comp format-timestamps schema/pb->CashAccount)
                           (:records result))
            links (when (seq accounts)
                    (build-links {:accounts accounts
                                  :has-more (:has-more result)
                                  :after after-id
                                  :before before-id}))]
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
           :body {:cash-account (format-timestamps (schema/pb->CashAccount
                                                    result))}})))
