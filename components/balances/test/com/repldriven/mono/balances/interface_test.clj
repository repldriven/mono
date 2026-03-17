(ns ^:eftest/synchronized
  com.repldriven.mono.balances.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.fdb.interface

    [com.repldriven.mono.balances.interface :as SUT]

    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :store])})

(deftest create-balance-test
  (with-test-system
   [sys "classpath:balances/application-test.yml"]
   (let [config (fdb-config sys)]
     (testing "creates a balance with zero totals"
       (nom-test>
        [balance (SUT/new-balance config
                             {:account-id "acc_123"
                              :balance-type :balance-type-default
                              :balance-status :balance-status-posted
                              :currency "USD"})
         _ (is (= "acc_123" (:account-id balance)))
         _ (is (= :balance-type-default
                   (:balance-type balance)))
         _ (is (= :balance-status-posted
                   (:balance-status balance)))
         _ (is (= "USD" (:currency balance)))
         _ (is (= 0 (:credit balance)))
         _ (is (= 0 (:debit balance)))
         _ (is (pos? (:created-at balance)))
         _ (is (pos? (:updated-at balance)))]))

     (testing "loads balance by composite key"
       (nom-test>
        [result (SUT/get-balance config
                                 "acc_123"
                                 :balance-type-default
                                 "USD"
                                 :balance-status-posted)
         _ (is (some? result))
         _ (is (= "acc_123" (:account-id result)))
         _ (is (= "USD" (:currency result)))]))

     (testing "lists balances by account"
       (nom-test>
        [_ (SUT/new-balance config
                       {:account-id "acc_123"
                        :balance-type
                        :balance-type-interest-accrued
                        :balance-status :balance-status-posted
                        :currency "USD"})
         balances (SUT/get-balances config "acc_123")
         _ (is (= 2 (count balances)))])))))
