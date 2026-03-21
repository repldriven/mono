(ns ^:eftest/synchronized com.repldriven.mono.bank-bootstrap.interface-test
  (:require
    com.repldriven.mono.bank-bootstrap.interface
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.bank-bootstrap.core :as core]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn- seed-config
  []
  {:organization-name "Queenswood"
   :currencies ["GBP"]
   :balances [{:balance-type :balance-type-default
               :balance-status :balance-status-posted
               :currency "GBP"
               :credit 10000
               :debit 0}]})

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :store])})

(deftest bootstrap-test
  (with-test-system
   [sys "classpath:bank-bootstrap/application-test.yml"]
   (let [result (system/instance sys [:bootstrap :internal])]
     (testing "bootstrap returns map of IDs"
       (nom-test> [_ (is (map? result))
                   _ (is (string? (:organization-id result)))
                   _ (is (string? (:party-id result)))
                   _ (is (string? (:product-id result)))
                   _ (is (string? (:version-id result)))
                   _ (is (string? (:account-id result)))]))
     (testing "initial balance is credited"
       (let [config (fdb-config sys)]
         (nom-test> [balance (balances/get-balance config
                                                   (:account-id result)
                                                   :balance-type-default
                                                   "GBP"
                                                   :balance-status-posted)
                     _ (is (= 10000 (:credit balance)))])))
     (testing "bootstrap is idempotent on re-run"
       (let [config (fdb-config sys)
             seed (seed-config)]
         (nom-test> [result2 (core/bootstrap config seed)
                     _ (is (= (:organization-id result)
                              (:organization-id result2)))
                     _ (is (= (:party-id result) (:party-id result2)))
                     _ (is (= (:product-id result) (:product-id result2)))
                     _ (is (= (:account-id result) (:account-id result2)))]))))))
