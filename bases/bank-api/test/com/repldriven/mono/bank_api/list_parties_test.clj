(ns ^:eftest/synchronized com.repldriven.mono.bank-api.list-parties-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.bank-api.api :as api]
    [com.repldriven.mono.bank-api.cursor :as cursor]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.schemas.interface :as schema]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system
      nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(def ^:private test-org-id "org_test_list_parties")
(def ^:dynamic *base-url* "http://localhost:{PORT}")

(defn- seed-party
  "Inserts a party directly into FDB, asserts success."
  [record-db record-store party]
  (let [result (fdb/transact record-db
                             record-store
                             "parties"
                             (fn [store]
                               (fdb/save-record store
                                                (schema/Party->java party))))]
    (assert (not (error/anomaly? result))
            (str "seed-party failed: " (pr-str result)))))

(defn- list-parties-request
  [& [query-string]]
  (let [url (cond-> (str *base-url* "/v1/parties")
                    query-string
                    (str "?" query-string))]
    (http/request {:method :get :url url})))

(defn- get-party-request
  [party-id]
  (http/request {:method :get
                 :url (str *base-url* "/v1/parties/" party-id)}))

(deftest list-parties-test
  (with-test-system
   [sys
    ["classpath:bank-api/list-parties-test.yml"
     #(assoc-in % [:system/defs :server :handler] api/app)]]
   (let [record-db (system/instance sys [:fdb :record-db])
         record-store (system/instance sys [:fdb :store])
         jetty (system/instance sys [:server :jetty-adapter])
         ids (mapv #(format "py-%03d" %) (range 1 4))
         parties (mapv (fn [id]
                         {:organization-id test-org-id
                          :party-id id
                          :type :person
                          :display-name (str "Party " id)
                          :status :pending
                          :created-at 1700000000000
                          :updated-at 1700000000000})
                       ids)]
     (doseq [p parties] (seed-party record-db record-store p))
     (binding [*base-url* (server/http-local-url jetty)]
       (testing "lists all parties"
         (nom-test> [res (list-parties-request)
                     _ (is (= 200 (:status res)))
                     body (http/res->body res)
                     _ (is (= 3 (count (get body "parties"))))
                     _ (is (= "py-001"
                              (get (first (get body "parties")) "party-id")))
                     _ (is (nil? (get-in body ["links" "prev"])))
                     _ (is (nil? (get-in body ["links" "next"])))]))
       (testing "paginates with page[size]"
         (nom-test> [res (list-parties-request "page[size]=2")
                     _ (is (= 200 (:status res)))
                     body (http/res->body res)
                     _ (is (= 2 (count (get body "parties"))))
                     _ (is (some? (get-in body ["links" "next"])))
                     _ (is (nil? (get-in body ["links" "prev"])))]))
       (testing "paginates forward with page[after]"
         (let [after-cursor (cursor/encode "py-001")]
           (nom-test> [res (list-parties-request (str "page[after]="
                                                      after-cursor))
                       _ (is (= 200 (:status res)))
                       body (http/res->body res)
                       _ (is (= 2 (count (get body "parties"))))
                       _ (is (= "py-002"
                                (get (first (get body "parties")) "party-id")))
                       _ (is (some? (get-in body ["links" "prev"])))])))
       (testing "paginates backward with page[before]"
         (let [before-cursor (cursor/encode "py-003")]
           (nom-test> [res (list-parties-request (str "page[before]="
                                                      before-cursor))
                       _ (is (= 200 (:status res)))
                       body (http/res->body res)
                       _ (is (= 2 (count (get body "parties"))))
                       _ (is (= "py-001"
                                (get (first (get body "parties")) "party-id")))])))
       (testing "get party by id"
         (nom-test> [res (get-party-request "py-001")
                     _ (is (= 200 (:status res)))
                     body (http/res->body res)
                     _ (is (= "py-001" (get body "party-id")))]))
       (testing "get party returns 404 for unknown id"
         (nom-test> [res (get-party-request "py-unknown")
                     _ (is (= 404 (:status res)))]))))))
