(ns ^:eftest/synchronized com.repldriven.mono.accounts.interface-test
  (:require
    com.repldriven.mono.accounts.interface
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.schemas.interface :as schema]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(def ^:private test-org-id "org_test_accounts")
(def ^:private test-product-id "prd_test_accounts")

(defn- send-command
  [proc schemas command-name data]
  (let [payload (avro/serialize (get schemas command-name) data)]
    (if (error/anomaly? payload)
      payload
      (processor/process proc {:command command-name :payload payload}))))

(defn- decode-payload
  [schemas schema-name result]
  (avro/deserialize-same (get schemas schema-name) (:payload result)))

(defn- poll-status
  "Polls get-account until account-status matches expected,
  or times out after 5 s."
  [proc schemas account-id expected]
  (loop [attempts 50]
    (let [result
          (send-command proc
                        schemas
                        "get-account"
                        {:organization-id test-org-id
                         :account-id account-id})
          decoded (when (= "ACCEPTED" (:status result))
                    (decode-payload schemas "account" result))]
      (cond
       (= expected (:account-status decoded))
       decoded
       (pos? attempts)
       (do (Thread/sleep 100) (recur (dec attempts)))
       :else
       decoded))))

(defn- seed-active-party
  "Seeds an active party record in the parties store."
  [record-db store-fn party-id]
  (fdb/transact record-db
                store-fn
                "parties"
                (fn [store]
                  (let [party {:organization-id test-org-id
                               :party-id party-id
                               :type :person
                               :status :active
                               :display-name "Test Party"
                               :created-at (System/currentTimeMillis)
                               :updated-at (System/currentTimeMillis)}]
                    (fdb/save-record
                     store
                     (schema/Party->java party))))))

(defn- seed-published-product-version
  "Seeds a published AccountProductVersion in the
  account-product-versions store."
  ([record-db store-fn product-id]
   (seed-published-product-version
    record-db
    store-fn
    product-id
    []))
  ([record-db store-fn product-id allowed-currencies]
   (fdb/transact record-db
                 store-fn
                 "account-product-versions"
                 (fn [store]
                   (let [version
                         {:organization-id test-org-id
                          :product-id product-id
                          :version-id "prv_test_001"
                          :version-number 1
                          :status "published"
                          :account-type :current
                          :balance-sheet-side :liability
                          :name "Test Product"
                          :allowed-currencies
                          allowed-currencies
                          :created-at
                          (System/currentTimeMillis)
                          :updated-at
                          (System/currentTimeMillis)}]
                     (fdb/save-record
                      store
                      (schema/AccountProductVersion->java
                       version)))))))

(defn- seed-party
  "Seeds a party record with given status."
  [record-db store-fn party-id status]
  (fdb/transact record-db
                store-fn
                "parties"
                (fn [store]
                  (let [party {:organization-id test-org-id
                               :party-id party-id
                               :type :person
                               :status status
                               :display-name "Test Party"
                               :created-at (System/currentTimeMillis)
                               :updated-at (System/currentTimeMillis)}]
                    (fdb/save-record
                     store
                     (schema/Party->java party))))))

(defn- test-open-account
  [proc schemas record-db store-fn]
  (testing
    "open-account creates account with opened status
  and payment addresses"
    (let [party-id "cust-1"
          open-payload
          {:organization-id test-org-id
           :party-id party-id
           :name "Test Account"
           :currency "USD"
           :product-id test-product-id}]
      (seed-active-party record-db store-fn party-id)
      (seed-published-product-version record-db
                                      store-fn
                                      test-product-id)
      (nom-test>
        [result (send-command proc
                              schemas
                              "open-account"
                              open-payload)
         _ (is (= "ACCEPTED" (:status result)))
         decoded (decode-payload schemas "account" result)
         _ (is (some? (:account-id decoded)))
         _ (is (= :opened (:account-status decoded)))
         _ (is (= test-product-id
                  (:product-id decoded)))
         _ (is (string? (:version-id decoded)))
         _ (is (= open-payload
                  (select-keys decoded
                               (keys open-payload))))
         _ (is (= "040004"
                  (get-in decoded
                          [:payment-addresses 0
                           :scan :sort-code])))
         _ (is (some? (get-in decoded
                              [:payment-addresses 0
                               :scan
                               :account-number])))]))))

(defn- test-open-account-party-not-active
  [proc schemas record-db store-fn]
  (testing "open-account rejects when party is not active"
    (let [party-id "cust-pending"]
      (seed-party record-db store-fn party-id :pending)
      (let [result (send-command proc
                                 schemas
                                 "open-account"
                                 {:organization-id test-org-id
                                  :party-id party-id
                                  :name "Pending Account"
                                  :currency "USD"
                                  :product-id test-product-id})]
        (is (error/rejection? result))
        (is (= :account/party-pending (error/kind result)))))))

(defn- test-open-account-party-not-found
  [proc schemas]
  (testing "open-account rejects when party not found"
    (let [result (send-command proc
                               schemas
                               "open-account"
                               {:organization-id test-org-id
                                :party-id "nonexistent"
                                :name "Ghost Account"
                                :currency "USD"
                                :product-id test-product-id})]
      (is (error/rejection? result))
      (is (= :account/party-unknown (error/kind result))))))

(defn- test-close-account
  [proc schemas record-db store-fn]
  (testing "close-account sets status to closing"
    (let [party-id "cust-2"
          open-payload
          {:organization-id test-org-id
           :party-id party-id
           :name "Account to Close"
           :currency "USD"
           :product-id test-product-id}]
      (seed-active-party record-db store-fn party-id)
      (nom-test>
        [opened (send-command proc
                              schemas
                              "open-account"
                              open-payload)
         account (decode-payload schemas "account" opened)
         close-data (select-keys account
                                 [:organization-id :account-id])
         result (send-command proc
                              schemas
                              "close-account"
                              close-data)
         _ (is (= "ACCEPTED" (:status result)))
         decoded (decode-payload schemas "account" result)
         _ (is (= open-payload
                  (select-keys decoded
                               (keys open-payload))))]))))

(defn- test-watcher-transitions
  [proc schemas record-db store-fn]
  (testing "watcher transitions closing->closed"
    (let [party-id "cust-watcher"
          open-payload
          {:organization-id test-org-id
           :party-id party-id
           :name "Watcher Account"
           :currency "GBP"
           :product-id test-product-id}]
      (seed-active-party record-db store-fn party-id)
      (nom-test>
        [opened (send-command proc
                              schemas
                              "open-account"
                              open-payload)
         account (decode-payload schemas "account" opened)
         account-id (:account-id account)
         _ (is (= :opened (:account-status account)))
         closed (send-command proc
                              schemas
                              "close-account"
                              {:organization-id test-org-id
                               :account-id account-id})
         closing-account (decode-payload schemas
                                         "account"
                                         closed)
         _ (is (= :closing (:account-status closing-account)))
         polled-closed (poll-status proc
                                    schemas
                                    account-id
                                    :closed)
         _ (is (= :closed (:account-status polled-closed)))]))))

(defn- test-get-account
  [proc schemas record-db store-fn]
  (testing "get-account returns account"
    (let [party-id "cust-7"
          open-payload
          {:organization-id test-org-id
           :party-id party-id
           :name "Status Account"
           :currency "USD"
           :product-id test-product-id}]
      (seed-active-party record-db store-fn party-id)
      (nom-test>
        [opened (send-command proc
                              schemas
                              "open-account"
                              open-payload)
         account (decode-payload schemas "account" opened)
         get-data (select-keys account
                               [:organization-id :account-id])
         result (send-command proc
                              schemas
                              "get-account"
                              get-data)
         _ (is (= "ACCEPTED" (:status result)))
         decoded (decode-payload schemas "account" result)
         _ (is (= (:account-id account)
                  (:account-id decoded)))
         _ (is (= :opened (:account-status decoded)))]))))

(defn- test-close-missing-account
  [proc schemas]
  (testing "close-account rejects missing account"
    (let [result (send-command proc
                               schemas
                               "close-account"
                               {:organization-id test-org-id
                                :account-id "missing-id"})]
      (is (error/rejection? result))
      (is (= :account/not-found (error/kind result))))))

(defn- test-open-multiple-accounts
  [proc schemas record-db store-fn]
  (testing "open-account allows multiple accounts per customer"
    (let [party-id "cust-multi"
          payload {:organization-id test-org-id
                   :party-id party-id
                   :currency "USD"
                   :product-id test-product-id}]
      (seed-active-party record-db store-fn party-id)
      (nom-test>
        [r1 (send-command proc
                          schemas
                          "open-account"
                          (assoc payload :name "Account A"))
         _ (is (= "ACCEPTED" (:status r1)))
         r2 (send-command proc
                          schemas
                          "open-account"
                          (assoc payload :name "Account B"))
         _ (is (= "ACCEPTED" (:status r2)))
         a1 (decode-payload schemas "account" r1)
         a2 (decode-payload schemas "account" r2)
         _ (is (not= (:account-id a1) (:account-id a2)))]))))

(defn- test-open-account-no-published-version
  [proc schemas]
  (testing
    "open-account rejects when no published version exists"
    (let [result
          (send-command proc
                        schemas
                        "open-account"
                        {:organization-id test-org-id
                         :party-id "cust-1"
                         :name "No Version"
                         :currency "USD"
                         :product-id "prd_no_versions"})]
      (is (error/rejection? result))
      (is (= :account/product-not-published
             (error/kind result))))))

(defn- test-open-account-invalid-currency
  [proc schemas record-db store-fn]
  (testing
    "open-account rejects when currency not in
  allowed-currencies"
    (let [party-id "cust-currency"
          product-id "prd_gbp_only"]
      (seed-active-party record-db store-fn party-id)
      (seed-published-product-version record-db
                                      store-fn
                                      product-id
                                      ["GBP"])
      (let [result
            (send-command proc
                          schemas
                          "open-account"
                          {:organization-id test-org-id
                           :party-id party-id
                           :name "Wrong Currency"
                           :currency "USD"
                           :product-id product-id})]
        (is (error/rejection? result))
        (is (= :account/invalid-currency
               (error/kind result)))))))

(defn- test-unknown-command
  [proc schemas]
  (testing "unknown command returns rejection"
    (let [result
          (send-command proc
                        schemas
                        "unknown-command"
                        {:organization-id test-org-id
                         :account-id "acc-8"})]
      (is (error/rejection? result))
      (is (= :accounts/unknown-command (error/kind result))))))

(deftest process-accounts-test
  (with-test-system
   [sys "classpath:accounts/application-test.yml"]
   (let [proc (system/instance sys [:accounts :processor])
         schemas (system/instance sys [:avro :serde])
         record-db (system/instance sys [:fdb :record-db])
         store-fn (system/instance sys [:fdb :store])]
     (test-open-account proc schemas record-db store-fn)
     (test-open-account-party-not-active proc schemas record-db store-fn)
     (test-open-account-party-not-found proc schemas)
     (test-close-account proc schemas record-db store-fn)
     (test-watcher-transitions proc schemas record-db store-fn)
     (test-get-account proc schemas record-db store-fn)
     (test-close-missing-account proc schemas)
     (test-open-multiple-accounts proc schemas record-db store-fn)
     (test-open-account-no-published-version proc schemas)
     (test-open-account-invalid-currency proc schemas record-db store-fn)
     (test-unknown-command proc schemas))))
