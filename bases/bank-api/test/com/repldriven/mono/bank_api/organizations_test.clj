(ns ^:eftest/synchronized com.repldriven.mono.bank-api.organizations-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.fdb.interface
    [com.repldriven.mono.bank-api.api :as api]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing]]))

(def ^:private admin-api-key (System/getenv "MONO_ADMIN_API_KEY"))

(defn- post-organization
  [base-url org-name currencies token]
  (http/request {:method :post
                 :url (str base-url "/v1/organizations")
                 :headers (cond-> {"Content-Type" "application/json"}
                                  token
                                  (assoc "Authorization"
                                         (str "Bearer " token)))
                 :body (json/write-str {"name" org-name
                                        "currencies" currencies})}))

(deftest create-organization-test
  (with-test-system
   [sys
    ["classpath:bank-api/organizations-test.yml"
     #(assoc-in % [:system/defs :server :handler] api/app)]]
   (let [jetty (system/instance sys [:server :jetty-adapter])
         base-url (server/http-local-url jetty)]
     (testing "admin can create an organization"
       (nom-test> [res (post-organization base-url
                                          "Acme Corp"
                                          ["GBP"]
                                          admin-api-key)
                   _ (is (= 201 (:status res)))
                   body (http/res->body res)
                   _ (is (= "Acme Corp" (get body "name")))
                   _ (is (string? (get body "organization-id")))
                   _ (is (string? (get body "api-key-secret")))
                   _ (is (.startsWith ^String (get body "api-key-secret")
                                      "sk_live_"))
                   api-key (get body "api-key")
                   _ (is (string? (get api-key "key-prefix")))
                   party (get body "party")
                   _ (is (string? (get party "party-id")))
                   accounts (get body "accounts")
                   _ (is (= 1 (count accounts)))
                   _ (is (string? (get (first accounts) "account-id")))
                   _ (is (seq (get (first accounts) "balances")))]))
     (testing "unauthenticated request returns 401"
       (nom-test> [res (post-organization base-url "No Auth Org" ["GBP"] nil)
                   _ (is (= 401 (:status res)))]))
     (testing "wrong key returns 401"
       (nom-test> [res (post-organization base-url
                                          "Bad Key Org"
                                          ["GBP"]
                                          "wrong-key")
                   _ (is (= 401 (:status res)))]))
     (testing "org key returns 403 on admin endpoint"
       (nom-test> [res (post-organization base-url
                                          "First Org"
                                          ["GBP"]
                                          admin-api-key)
                   _ (is (= 201 (:status res)))
                   body (http/res->body res)
                   org-key (get body "api-key-secret")
                   res2
                   (post-organization base-url "Second Org" ["GBP"] org-key)
                   _ (is (= 403 (:status res2)))])))))
