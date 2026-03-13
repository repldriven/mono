(ns ^:eftest/synchronized com.repldriven.mono.account-products.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.fdb.interface

    [com.repldriven.mono.account-products.interface :as SUT]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :store])})

(deftest create-product-test
  (with-test-system
   [sys "classpath:account-products/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_01"]
     (testing "creates product as initial draft v1"
       (nom-test> [result (SUT/new-product config
                                           org-id
                                           {:name "Current Account"
                                            :account-type "CURRENT"
                                            :balance-sheet-side "LIABILITY"
                                            :allowed-currencies ["GBP" "USD"]})
                   version (:version result)
                   _ (is (= "Current Account" (:name version)))
                   _ (is (string? (:product-id version)))
                   _ (is (= org-id (:organization-id version)))
                   _ (is (= 1 (:version-number version)))
                   _ (is (= "draft" (:status version)))
                   _ (is (= :current (:account-type version)))
                   _ (is (= :liability (:balance-sheet-side version)))
                   _ (is (= ["GBP" "USD"] (:allowed-currencies version)))
                   _ (is (pos? (:created-at version)))])))))

(deftest version-lifecycle-test
  (with-test-system
   [sys "classpath:account-products/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_02"]
     (testing "create additional version after publishing"
       (nom-test> [result (SUT/new-product config
                                           org-id
                                           {:name "Term Deposit"
                                            :account-type "TERM_DEPOSIT"
                                            :balance-sheet-side "LIABILITY"})
                   product-id (get-in result [:version :product-id])
                   v1-id (get-in result [:version :version-id])
                   _ (SUT/publish config org-id product-id v1-id)
                   v2-result (SUT/new-version config
                                              org-id
                                              product-id
                                              {:name "Term Deposit"
                                               :account-type "TERM_DEPOSIT"
                                               :balance-sheet-side "LIABILITY"
                                               :valid-from "2025-01-01"})
                   v2 (:version v2-result)
                   _ (is (= 2 (:version-number v2)))
                   _ (is (= "Term Deposit" (:name v2)))
                   _ (is (= "draft" (:status v2)))
                   _ (is (= "2025-01-01" (:valid-from v2)))]))
     (testing "reject new version when latest is draft"
       (nom-test> [result (SUT/new-product config
                                           org-id
                                           {:name "Draft Guard Test"
                                            :account-type "CURRENT"
                                            :balance-sheet-side "ASSET"})
                   product-id (get-in result [:version :product-id])
                   _ (let [rejected (SUT/new-version config
                                                     org-id
                                                     product-id
                                                     {:name "Draft Guard Test"
                                                      :account-type "CURRENT"
                                                      :balance-sheet-side
                                                      "ASSET"})]
                       (is (error/anomaly? rejected))
                       (is (= :account-products/draft-exists
                              (error/kind rejected))))]))
     (testing "get and list versions"
       (nom-test> [result (SUT/new-product config
                                           org-id
                                           {:name "Get Version Test"
                                            :account-type "CURRENT"
                                            :balance-sheet-side "ASSET"})
                   product-id (get-in result [:version :product-id])
                   version-id (get-in result [:version :version-id])
                   loaded (SUT/get-version config org-id product-id version-id)
                   _ (is (= :current (:account-type loaded)))
                   _ (is (= 1 (:version-number loaded)))
                   versions (SUT/get-versions config org-id product-id)
                   _ (is (= 1 (count (:versions versions))))]))
     (testing "publish version"
       (nom-test> [result (SUT/new-product config
                                           org-id
                                           {:name "Publish Test"
                                            :account-type "CURRENT"
                                            :balance-sheet-side "ASSET"})
                   version-id (get-in result [:version :version-id])
                   product-id (get-in result [:version :product-id])
                   published (SUT/publish config org-id product-id version-id)
                   _ (is (= "published" (:status published)))]))
     (testing "reject double publish"
       (let [result (SUT/new-product config
                                     org-id
                                     {:name "Double Publish Test"
                                      :account-type "CURRENT"
                                      :balance-sheet-side "ASSET"})
             version-id (get-in result [:version :version-id])
             product-id (get-in result [:version :product-id])]
         (SUT/publish config org-id product-id version-id)
         (is (error/anomaly?
              (SUT/publish config org-id product-id version-id))))))))

(deftest get-published-version-test
  (with-test-system
   [sys "classpath:account-products/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_03"]
     (testing "returns nil when no published version"
       (nom-test> [result (SUT/new-product config
                                           org-id
                                           {:name "Unpublished"
                                            :account-type "CURRENT"
                                            :balance-sheet-side "ASSET"})
                   product-id (get-in result [:version :product-id])
                   published (SUT/get-published config org-id product-id)
                   _ (is (nil? published))]))
     (testing "returns published v1, then published v2"
       (nom-test> [result (SUT/new-product config
                                           org-id
                                           {:name "Versioned Product"
                                            :account-type "CURRENT"
                                            :balance-sheet-side "LIABILITY"})
                   product-id (get-in result [:version :product-id])
                   v1-id (get-in result [:version :version-id])
                   _ (SUT/publish config org-id product-id v1-id)
                   v2-result (SUT/new-version config
                                              org-id
                                              product-id
                                              {:name "Versioned Product"
                                               :account-type "CURRENT"
                                               :balance-sheet-side "LIABILITY"})
                   current (SUT/get-published config org-id product-id)
                   _ (is (= 1 (:version-number current)))
                   _ (is (= "published" (:status current)))
                   v2-id (get-in v2-result [:version :version-id])
                   _ (SUT/publish config org-id product-id v2-id)
                   current2 (SUT/get-published config org-id product-id)
                   _ (is (= 2 (:version-number current2)))
                   _ (is (= "published" (:status current2)))])))))
