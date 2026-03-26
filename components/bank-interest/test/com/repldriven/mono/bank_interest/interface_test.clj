(ns ^:eftest/synchronized com.repldriven.mono.bank-interest.interface-test
  (:require
    com.repldriven.mono.bank-bootstrap.interface
    com.repldriven.mono.bank-interest.interface
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface :as
     cash-accounts]
    [com.repldriven.mono.bank-cash-account-product.interface :as
     products]
    [com.repldriven.mono.bank-organization.interface :as
     organizations]
    [com.repldriven.mono.bank-transaction.interface :as
     transactions]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn- send-command
  [proc schemas command-name data]
  (let [payload (avro/serialize (get schemas command-name)
                                data)]
    (if (error/anomaly? payload)
      payload
      (processor/process proc
                         {:command command-name
                          :payload payload}))))

(defn- decode-payload
  [schemas schema-name result]
  (avro/deserialize-same (get schemas schema-name)
                         (:payload result)))

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :meta-store])})

(defn- poll-account-opened
  "Polls until the account status is opened, or times out."
  [config org-id account-id]
  (loop [attempts 50]
    (let [accounts (cash-accounts/get-accounts config
                                               org-id)
          account (when-not (error/anomaly? accounts)
                    (first (filter
                            #(= account-id
                                (:account-id %))
                            accounts)))]
      (cond (= :cash-account-status-opened
               (:account-status account))
            account
            (pos? attempts)
            (do (Thread/sleep 100)
                (recur (dec attempts)))
            :else
            account))))

(defn- create-funded-customer
  "Creates a customer org with a savings product at the
  given interest rate, opens an account, waits for opened
  status, and funds it. Returns {:organization-id
  :account-id}."
  [config internal-account-id org-name
   interest-rate-bps fund-amount]
  (let [org-result (organizations/new-organization
                    config
                    org-name
                    :organisation-type-customer
                    ["GBP"])
        org-id (get-in org-result
                       [:organization :organization-id])
        party-id (get-in org-result
                         [:organization :party :party-id])
        product (products/new-product
                 config
                 org-id
                 {:name "Savings Account"
                  :account-type :account-type-savings
                  :balance-sheet-side
                  :balance-sheet-side-liability
                  :allowed-currencies ["GBP"]
                  :balance-products
                  [{:balance-type :balance-type-default
                    :balance-status :balance-status-posted}
                   {:balance-type
                    :balance-type-interest-accrued
                    :balance-status
                    :balance-status-posted}]
                  :allowed-payment-address-schemes
                  [:payment-address-scheme-scan]
                  :interest-rate-bps interest-rate-bps})
        product-id (get-in product [:version :product-id])
        version-id (get-in product [:version :version-id])
        _ (products/publish config
                            org-id
                            product-id
                            version-id)
        account
        (cash-accounts/new-account
         config
         {:organization-id org-id
          :party-id party-id
          :name "Test Savings"
          :currency "GBP"
          :product-id product-id})
        account-id (:account-id account)
        _ (poll-account-opened config org-id account-id)
        _ (let [{:keys [record-db record-store]} config
                txn-data
                {:idempotency-key (str "fund-" org-name)
                 :transaction-type
                 :transaction-type-internal-transfer
                 :currency "GBP"
                 :reference "Fund for interest test"
                 :legs
                 [{:account-id internal-account-id
                   :balance-type :balance-type-suspense
                   :balance-status :balance-status-posted
                   :side :leg-side-debit
                   :amount fund-amount}
                  {:account-id account-id
                   :balance-type :balance-type-default
                   :balance-status :balance-status-posted
                   :side :leg-side-credit
                   :amount fund-amount}]}]
            (fdb/transact-multi
             record-db
             record-store
             (fn [open-store]
               (let [result (transactions/record-transaction
                             open-store
                             txn-data)]
                 (balances/apply-legs
                  (open-store "balances")
                  (:legs result))))))]
    {:organization-id org-id :account-id account-id}))

(defn- test-accrue-daily-interest
  [sys proc schemas]
  (testing "accrue-daily-interest accrues for accounts
  with interest rate"
    (let [config (fdb-config sys)
          internal (system/instance sys
                                    [:bootstrap :internal])
          internal-id (:account-id internal)]
      (nom-test>
        [customer (create-funded-customer
                   config
                   internal-id
                   "Accrue Customer"
                   500
                   100000)
         result (send-command
                 proc
                 schemas
                 "accrue-daily-interest"
                 {:idempotency-key "accrue-001"
                  :organization-id
                  (:organization-id customer)
                  :as-of-date 20260324})
         _ (is (= "ACCEPTED" (:status result)))
         decoded (decode-payload schemas
                                 "interest-result"
                                 result)
         _ (is (pos? (:accounts-processed decoded)))
         accrued
         (balances/get-balance config
                               (:account-id customer)
                               :balance-type-interest-accrued
                               "GBP"
                               :balance-status-posted)
         _ (is (pos? (:credit accrued)))]))))

(defn- test-capitalize-monthly-interest
  [sys proc schemas]
  (testing "capitalize-monthly-interest moves accrued
  to default"
    (let [config (fdb-config sys)
          internal (system/instance sys
                                    [:bootstrap :internal])
          internal-id (:account-id internal)]
      (nom-test>
        [customer (create-funded-customer
                   config
                   internal-id
                   "Cap Customer"
                   500
                   100000)
         _ (send-command
            proc
            schemas
            "accrue-daily-interest"
            {:idempotency-key "accrue-cap-001"
             :organization-id
             (:organization-id customer)
             :as-of-date 20260324})
         accrued-before
         (balances/get-balance config
                               (:account-id customer)
                               :balance-type-interest-accrued
                               "GBP"
                               :balance-status-posted)
         accrued-amount (- (:credit accrued-before 0)
                           (:debit accrued-before 0))
         _ (is (pos? accrued-amount))
         default-before
         (balances/get-balance config
                               (:account-id customer)
                               :balance-type-default
                               "GBP"
                               :balance-status-posted)
         result (send-command
                 proc
                 schemas
                 "capitalize-monthly-interest"
                 {:idempotency-key "cap-001"
                  :organization-id
                  (:organization-id customer)
                  :as-of-date 20260324})
         _ (is (= "ACCEPTED" (:status result)))
         accrued-after
         (balances/get-balance config
                               (:account-id customer)
                               :balance-type-interest-accrued
                               "GBP"
                               :balance-status-posted)
         _ (is (= 0
                  (- (:credit accrued-after 0)
                     (:debit accrued-after 0))))
         default-after
         (balances/get-balance config
                               (:account-id customer)
                               :balance-type-default
                               "GBP"
                               :balance-status-posted)
         _ (is (= (+ (:credit default-before)
                     accrued-amount)
                  (:credit default-after)))]))))

(defn- test-unknown-command
  [proc schemas]
  (testing "unknown command returns rejection"
    (let [result
          (send-command
           proc
           schemas
           "unknown-interest-command"
           {:idempotency-key "idem-999"
            :organization-id "org-1"
            :as-of-date 20260324})]
      (is (error/rejection? result))
      (is (= :interest/unknown-command
             (error/kind result))))))

(deftest process-interest-test
  (with-test-system [sys "classpath:bank-interest/application-test.yml"]
                    (let [proc (system/instance sys [:interest :processor])
                          schemas (system/instance sys [:avro :serde])]
                      (test-accrue-daily-interest sys proc schemas)
                      (test-capitalize-monthly-interest sys proc schemas)
                      (test-unknown-command proc schemas))))
