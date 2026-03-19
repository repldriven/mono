(ns ^:eftest/synchronized com.repldriven.mono.bank-api.list-cash-accounts-test
  (:require
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.bank-api.api :as api]
    [com.repldriven.mono.bank-api.cursor :as cursor]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.bank-schema.interface :as schema]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [clojure.test :refer [deftest is testing]]))

(def ^:private test-org-id "org_test_list_cash_accounts")
(def ^:dynamic *base-url* "http://localhost:{PORT}")

(defn- seed-account
  "Inserts an account directly into FDB."
  [record-db record-store account]
  (fdb/transact record-db
                record-store
                "cash-accounts"
                (fn [store]
                  (fdb/save-record store (schema/CashAccount->java account)))))

(defn- list-cash-accounts-request
  [& [query-string]]
  (let [url (cond-> (str *base-url* "/v1/cash-accounts")
                    query-string
                    (str "?" query-string))]
    (http/request {:method :get :url url})))

(deftest list-cash-accounts-test
  (with-test-system
   [sys
    ["classpath:bank-api/list-cash-accounts-test.yml"
     #(assoc-in % [:system/defs :server :handler] api/app)]]
   (let [record-db (system/instance sys [:fdb :record-db])
         record-store (system/instance sys [:fdb :store])
         jetty (system/instance sys [:server :jetty-adapter])
         ids (mapv #(format "acct-%03d" %) (range 1 4))
         accounts (mapv (fn [id]
                          {:organization-id test-org-id
                           :account-id id
                           :party-id (str "cust-" id)
                           :name (str "Account " id)
                           :currency "GBP"
                           :product-id "prd_test_list"
                           :version-id "prv_test_list"
                           :account-status :cash-account-status-opened})
                        ids)]
     (doseq [a accounts] (seed-account record-db record-store a))
     (binding [*base-url* (server/http-local-url jetty)]
       (testing "lists all accounts"
         (nom-test> [res (list-cash-accounts-request)
                     _ (is (= 200 (:status res)))
                     body (http/res->body res)
                     _ (is (= 3 (count (get body "cash-accounts"))))
                     _ (is (= "acct-001"
                              (get (first (get body "cash-accounts"))
                                   "account-id")))
                     _ (is (nil? (get-in body ["links" "prev"])))
                     _ (is (nil? (get-in body ["links" "next"])))]))
       (testing "paginates with page[size]"
         (nom-test> [res (list-cash-accounts-request "page[size]=2")
                     _ (is (= 200 (:status res)))
                     body (http/res->body res)
                     _ (is (= 2 (count (get body "cash-accounts"))))
                     _ (is (some? (get-in body ["links" "next"])))
                     _ (is (nil? (get-in body ["links" "prev"])))]))
       (testing "paginates forward with page[after]"
         (let [after-cursor (cursor/encode "acct-001")]
           (nom-test> [res (list-cash-accounts-request (str "page[after]="
                                                            after-cursor))
                       _ (is (= 200 (:status res)))
                       body (http/res->body res)
                       _ (is (= 2 (count (get body "cash-accounts"))))
                       _ (is (= "acct-002"
                                (get (first (get body "cash-accounts"))
                                     "account-id")))
                       _ (is (some? (get-in body ["links" "prev"])))])))
       (testing "paginates backward with page[before]"
         (let [before-cursor (cursor/encode "acct-003")]
           (nom-test> [res (list-cash-accounts-request (str "page[before]="
                                                            before-cursor))
                       _ (is (= 200 (:status res)))
                       body (http/res->body res)
                       _ (is (= 2 (count (get body "cash-accounts"))))
                       _ (is (= "acct-001"
                                (get (first (get body "cash-accounts"))
                                     "account-id")))])))
       (testing "returns empty when no accounts match"
         (let [after-cursor (cursor/encode "acct-999")]
           (nom-test> [res (list-cash-accounts-request (str "page[after]="
                                                            after-cursor))
                       _ (is (= 200 (:status res)))
                       body (http/res->body res)
                       _ (is (= 0 (count (get body "cash-accounts"))))])))))))
