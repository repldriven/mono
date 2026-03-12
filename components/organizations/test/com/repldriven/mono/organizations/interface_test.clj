(ns ^:eftest/synchronized com.repldriven.mono.organizations.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.fdb.interface

    [com.repldriven.mono.organizations.interface :as SUT]

    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :store])})

(deftest create-organization-test
  (with-test-system
   [sys "classpath:organizations/application-test.yml"]
   (let [config (fdb-config sys)]
     (testing "creates organization and api-key atomically"
       (nom-test> [result (SUT/create-organization config "Acme Corp")
                   org (:organization result)
                   _ (is (= "Acme Corp" (:name org)))
                   _ (is (string? (:organization-id org)))
                   _ (is (= "active" (:status org)))
                   _ (is (pos? (:created-at org)))
                   api-key (:api-key result)
                   _ (is (= (:organization-id org) (:organization-id api-key)))
                   _ (is (= "default" (:name api-key)))
                   _ (is (string? (:key-hash api-key)))
                   _ (is (string? (:key-prefix api-key)))
                   raw-key (:raw-key result)
                   _ (is (string? raw-key))
                   _ (is (.startsWith ^String raw-key "sk_live_"))])))))

(deftest verify-api-key-test
  (with-test-system
   [sys "classpath:organizations/application-test.yml"]
   (let [config (fdb-config sys)]
     (testing "round-trip: create then verify"
       (nom-test> [result (SUT/create-organization config "Verify Org")
                   verified (SUT/verify-api-key config (:raw-key result))
                   _ (is (some? verified))
                   _ (is (= (:id (:api-key result)) (:id verified)))]))
     (testing "returns nil for unknown key"
       (is (nil? (SUT/verify-api-key config "sk_live_nonexistent")))))))

(deftest find-api-key-by-hash-test
  (with-test-system
   [sys "classpath:organizations/application-test.yml"]
   (let [config (fdb-config sys)]
     (testing "finds key by hash"
       (nom-test> [result (SUT/create-organization config "Hash Org")
                   found (SUT/find-api-key-by-hash config
                                                   (:key-hash (:api-key
                                                               result)))
                   _ (is (some? found))
                   _ (is (= (:id (:api-key result)) (:id found)))
                   _ (is (= (:organization-id (:organization result))
                            (:organization-id found)))])))))
