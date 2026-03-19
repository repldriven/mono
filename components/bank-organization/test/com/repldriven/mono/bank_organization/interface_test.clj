(ns ^:eftest/synchronized com.repldriven.mono.bank-organization.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.fdb.interface
    [com.repldriven.mono.bank-organization.interface :as SUT]
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
   [sys "classpath:bank-organization/application-test.yml"]
   (let [config (fdb-config sys)]
     (testing "creates organization and api-key atomically"
       (nom-test> [result (SUT/new-organization config "Acme Corp")
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
