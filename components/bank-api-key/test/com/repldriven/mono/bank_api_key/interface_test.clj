(ns ^:eftest/synchronized com.repldriven.mono.bank-api-key.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.fdb.interface
    [com.repldriven.mono.bank-api-key.interface :as SUT]
    [com.repldriven.mono.bank-organization.interface :as organizations]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [clojure.test :refer [deftest is testing]]))

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :store])})

(deftest get-api-key-test
  (with-test-system
   [sys "classpath:bank-api-key/application-test.yml"]
   (let [config (fdb-config sys)]
     (testing "finds key by hash"
       (nom-test> [result (organizations/new-organization config "Hash Org")
                   found (SUT/get-api-key config (:key-hash (:api-key result)))
                   _ (is (some? found))
                   _ (is (= (:id (:api-key result)) (:id found)))
                   _ (is (= (:organization-id (:organization result))
                            (:organization-id found)))])))))

(deftest get-api-keys-test
  (with-test-system
   [sys "classpath:bank-api-key/application-test.yml"]
   (let [config (fdb-config sys)]
     (testing "lists api keys for an organization"
       (nom-test> [result (organizations/new-organization config "List Org")
                   org-id (:organization-id (:organization result))
                   keys (SUT/get-api-keys config org-id)
                   _ (is (= 1 (count keys)))
                   k (first keys)
                   _ (is (= org-id (:organization-id k)))
                   _ (is (= "default" (:name k)))
                   _ (is (string? (:key-prefix k)))])))))
