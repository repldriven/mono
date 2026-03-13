(ns ^:eftest/synchronized com.repldriven.mono.service.processor-test
  (:require
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.accounts.interface
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schemas.interface :as schema]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn send-command
  "Simulates Sender - Avro-serializes payload, sends a
  command envelope via dispatcher, and blocks until the
  result is received."
  [sys command-name data]
  (let [dispatcher (system/instance sys [:accounts :dispatcher])
        schemas (system/instance sys [:avro :serde])
        schema (get schemas command-name)
        payload (avro/serialize schema data)
        cmd-id (str (java.util.UUID/randomUUID))]
    (telemetry/with-span ["send-command" {}]
                         (command/send dispatcher
                                       {:id cmd-id
                                        :command command-name
                                        :correlation-id cmd-id
                                        :causation-id nil
                                        :traceparent
                                        (telemetry/inject-traceparent)
                                        :tracestate nil
                                        :payload payload
                                        :reply-to nil}))))

(def ^:private test-org-id "org_test_processor")

(defn- seed-active-party
  [record-db store-fn party-id]
  (fdb/transact record-db
                store-fn
                "parties"
                (fn [store]
                  (fdb/save-record
                   store
                   (schema/Party->java
                    {:organization-id test-org-id
                     :party-id party-id
                     :type :person
                     :status :active
                     :display-name "Test Party"
                     :created-at (System/currentTimeMillis)
                     :updated-at (System/currentTimeMillis)})))))

(def ^:private test-product-id "prd_test_processor")

(defn- seed-published-product-version
  [record-db store-fn product-id]
  (fdb/transact record-db
                store-fn
                "account-product-versions"
                (fn [store]
                  (fdb/save-record
                   store
                   (schema/AccountProductVersion->java
                    {:organization-id test-org-id
                     :product-id product-id
                     :version-id "prv_test_001"
                     :version-number 1
                     :status "published"
                     :account-type :current
                     :balance-sheet-side :liability
                     :name "Test Product"
                     :allowed-currencies []
                     :created-at (System/currentTimeMillis)
                     :updated-at
                     (System/currentTimeMillis)})))))

(deftest process-command-test
  (testing "Commands sent are processed and replied to via message-bus"
    (with-test-system
     [sys "classpath:service/application-test.yml"]
     (let [record-db (system/instance sys [:fdb :record-db])
           store-fn (system/instance sys [:fdb :store])]
       (seed-active-party record-db store-fn "cust-api-test")
       (seed-published-product-version record-db store-fn test-product-id)
       (telemetry/with-span-tests
        [_ ["send-command" "process-command"]]
        (let [schemas (system/instance sys [:avro :serde])]
          (nom-test> [result (send-command sys
                                           "open-account"
                                           {:organization-id test-org-id
                                            :party-id "cust-api-test"
                                            :name "API Test Account"
                                            :currency "GBP"
                                            :product-id test-product-id})
                      _ (is (= "ACCEPTED" (:status result)))
                      decoded (avro/deserialize-same (get schemas "account")
                                                     (:payload result))
                      _ (is (some? (:account-id decoded)))])))))))
