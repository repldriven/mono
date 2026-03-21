(ns ^:eftest/synchronized com.repldriven.mono.bank-organization.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.fdb.interface
    com.repldriven.mono.bank-cash-account.interface

    [com.repldriven.mono.bank-organization.interface :as SUT]

    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :store])})

(deftest new-organization-test
  (with-test-system
   [sys "classpath:bank-organization/application-test.yml"]
   (let [config (fdb-config sys)]
     (testing "creates org with api-key, party, product, and accounts"
       (nom-test> [result (SUT/new-organization config
                                                "Test Org"
                                                :organisation-type-customer
                                                ["GBP" "USD"])
                   org (:organization result)
                   _ (is (= {:name "Test Org"
                             :type :organisation-type-customer
                             :status "active"}
                            (select-keys org [:name :type :status])))
                   _ (is (.startsWith ^String (:key-secret result) "sk_live_"))
                   _ (is (= {:type :party-type-organization
                             :status :party-status-active}
                            (select-keys (:party org) [:type :status])))
                   _ (is (= #{"GBP" "USD"}
                            (set (map :currency (:accounts org)))))
                   _ (is (every? #(= :cash-account-status-opened
                                     (:account-status %))
                                 (:accounts org)))
                   _ (is (every? #(seq (:balances %)) (:accounts org)))
                   _ (is (some? (:api-key org)))])))))
