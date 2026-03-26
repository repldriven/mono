(ns com.repldriven.mono.bank-interest.commands
  (:require
    [com.repldriven.mono.bank-interest.domain :as domain]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface :as
     cash-accounts]
    [com.repldriven.mono.bank-cash-account-product.interface :as
     products]
    [com.repldriven.mono.bank-schema.interface :as schema]
    [com.repldriven.mono.bank-transaction.interface :as
     transactions]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.cache.interface :as cache]
    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn- customer-accounts
  [accounts]
  (filter #(and (not= :account-type-internal
                      (:account-type %))
                (not= :account-type-settlement
                      (:account-type %))
                (= :cash-account-status-opened
                   (:account-status %)))
          accounts))

(def ^:private product-cache (cache/create 60000))

(defn- get-product-version
  [config org-id account]
  (cache/lookup
   product-cache
   [(:product-id account) (:version-id account)]
   #(products/get-version config
                          org-id
                          (:product-id account)
                          (:version-id account))))

(defn- load-balance
  "Loads a balance inside an open store."
  [store account-id balance-type currency]
  (let [record (fdb/load-record
                store
                account-id
                (schema/balance-type->int balance-type)
                currency
                (schema/balance-status->int
                 :balance-status-posted))]
    (when record (schema/pb->Balance record))))

(defn- save-balance
  "Saves a balance inside an open store."
  [store balance]
  (fdb/save-record store (schema/Balance->java balance)))

(defn- accrue-account
  "Accrues daily interest for a single account. Reads
  balance, calculates interest with carry, conditionally
  records transaction, always updates carry. All in one
  FDB transaction."
  [config settlement-id account product as-of-date]
  (let [{:keys [record-db record-store]} config
        {:keys [account-id currency]} account
        rate (:interest-rate-bps product 0)]
    (when-not (zero? rate)
      (fdb/transact-multi
       record-db
       record-store
       (fn [open-store]
         (let [balance-store (open-store "balances")
               balance (load-balance balance-store
                                     account-id
                                     :balance-type-default
                                     currency)
               existing-carry (:credit-carry balance 0)
               {:keys [whole-units carry]}
               (domain/daily-interest balance
                                      rate
                                      existing-carry)]
           ;; Record transaction when whole units > 0
           (when-let
             [txn-data
              (domain/accrual-transaction
               settlement-id
               account-id
               currency
               whole-units
               as-of-date)]
             (let-nom>
               [result (transactions/record-transaction
                        open-store
                        txn-data)]
               (balances/apply-legs balance-store
                                    (:legs result))))
           ;; Always update carry
           (save-balance
            balance-store
            (assoc balance :credit-carry carry))
           true))))))

(defn- capitalize-account
  [config settlement-id account as-of-date]
  (let [{:keys [record-db record-store]} config
        {:keys [account-id currency]} account]
    (fdb/transact-multi
     record-db
     record-store
     (fn [open-store]
       (let [balance-store (open-store "balances")
             balance (load-balance balance-store
                                   account-id
                                   :balance-type-interest-accrued
                                   currency)]
         (when-let
           [txn-data
            (domain/capitalization-transaction
             settlement-id
             account-id
             currency
             balance
             as-of-date)]
           (let-nom>
             [result (transactions/record-transaction
                      open-store
                      txn-data)]
             (balances/apply-legs balance-store
                                  (:legs result))
             true)))))))

(defn- ->response
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      (let-nom> [payload
                 (avro/serialize (schemas "interest-result")
                                 result)]
        {:status "ACCEPTED" :payload payload}))))

(defn- get-settlement-account
  [config organization-id]
  (let [result (cash-accounts/get-accounts-by-type
                config
                :account-type-settlement)]
    (when-not (error/anomaly? result)
      (first (filter #(= organization-id
                         (:organization-id %))
                     result)))))

(defn- process-customer-accounts
  [config organization-id settlement-id as-of-date f]
  (loop [cursor nil
         n 0]
    (let [page (cash-accounts/get-accounts
                config
                organization-id
                (when cursor {:after cursor}))]
      (if (error/anomaly? page)
        page
        (let [processed
              (reduce
               (fn [n account]
                 (let [result (f config
                                 settlement-id
                                 account
                                 as-of-date)]
                   (if (error/anomaly? result)
                     (reduced result)
                     (if result (inc n) n))))
               n
               (customer-accounts (:accounts page)))]
          (if (error/anomaly? processed)
            processed
            (if (:after page)
              (recur (:after page) processed)
              processed)))))))

(defn accrue-daily
  [config data]
  (->response
   config
   (let [{:keys [organization-id as-of-date]} data]
     (if-let [settlement (get-settlement-account
                          config
                          organization-id)]
       (let [processed
             (process-customer-accounts
              config
              organization-id
              (:account-id settlement)
              as-of-date
              (fn [config sid account as-of-date]
                (when-let [product
                           (get-product-version
                            config
                            organization-id
                            account)]
                  (accrue-account
                   config
                   sid
                   account
                   product
                   as-of-date))))]
         (if (error/anomaly? processed)
           processed
           {:organization-id organization-id
            :as-of-date as-of-date
            :accounts-processed processed}))
       (error/reject :interest/no-settlement
                     "No settlement account found")))))

(defn capitalize-monthly
  [config data]
  (->response
   config
   (let [{:keys [organization-id as-of-date]} data]
     (if-let [settlement (get-settlement-account
                          config
                          organization-id)]
       (let [processed
             (process-customer-accounts
              config
              organization-id
              (:account-id settlement)
              as-of-date
              capitalize-account)]
         (if (error/anomaly? processed)
           processed
           {:organization-id organization-id
            :as-of-date as-of-date
            :accounts-processed processed}))
       (error/reject :interest/no-settlement
                     "No settlement account found")))))
