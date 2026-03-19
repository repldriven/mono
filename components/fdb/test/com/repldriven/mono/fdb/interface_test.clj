(ns ^:eftest/synchronized com.repldriven.mono.fdb.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.fdb.interface :as SUT]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-schema.interface :as test-schema]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as utility]
    [clojure.test :refer [deftest is testing]]))

(defn- test-str-kv
  [sys]
  (let [db (system/instance sys [:fdb :db])]
    (testing "can store and retrieve string values as raw KV"
      (nom-test> [_ (SUT/set-str db "test-key" "test-value")
                  result
                  (SUT/get-str db "test-key")
                  _
                  (is (= "test-value" result))]))))

(defn- test-proto-kv
  [sys]
  (let [whiskers
        {:pet-id "pet-1" :name "Whiskers" :species "cat" :age-months 24}
        db (system/instance sys [:fdb :db])]
    (testing "can store and retrieve Pet records as raw KV"
      (nom-test> [_ (SUT/set-bytes db "pet/1" (test-schema/Pet->pb whiskers))
                  retrieved
                  (error/nom-> (SUT/get-bytes db "pet/1") test-schema/pb->Pet)
                  _
                  (is (= whiskers (utility/record->map retrieved)))]))))

(defn- test-record-layer
  [sys pet-store]
  (let [whiskers
        {:pet-id "pet-1" :name "Whiskers" :species "cat" :age-months 24}
        record-db (system/instance sys [:fdb :record-db])]
    (testing "can save and load Pet records via FDB Record Layer"
      (nom-test> [_
                  (SUT/transact
                   record-db
                   pet-store
                   "pets"
                   (fn [store]
                     (SUT/save-record store (test-schema/Pet->java whiskers))))
                  retrieved
                  (error/nom-> (SUT/transact record-db
                                             pet-store
                                             "pets"
                                             (fn [store]
                                               (SUT/load-record store "pet-1")))
                               test-schema/pb->Pet)
                  _
                  (is (= whiskers (utility/record->map retrieved)))]))))

(defn- test-record-layer-consumer
  [sys pet-store]
  (let [whiskers
        {:pet-id "pet-20" :name "Whiskers" :species "cat" :age-months 24}
        rex {:pet-id "pet-21" :name "Rex" :species "dog" :age-months 36}
        record-db (system/instance sys [:fdb :record-db])
        received (atom [])]
    (testing
      "consumer reads changelog entries and calls handler with
       record bytes"
      (nom-test>
        [_
         (SUT/transact record-db
                       pet-store
                       "pets"
                       (fn [store]
                         (SUT/save-record store
                                          (test-schema/Pet->java whiskers))
                         (SUT/write-changelog store
                                              "pets"
                                              (:pet-id whiskers)
                                              (.getBytes "whiskers-data"))
                         (SUT/save-record store (test-schema/Pet->java rex))
                         (SUT/write-changelog store
                                              "pets"
                                              (:pet-id rex)
                                              (.getBytes "rex-data"))))
         _
         (SUT/process-changelog record-db
                                "test-consumer"
                                "pets"
                                (fn [_ctx changelog-bytes]
                                  (swap! received conj changelog-bytes)))
         _
         (is (= 2 (count @received)))
         _
         (is (= "whiskers-data" (String. ^bytes (first @received))))
         _
         (is (= "rex-data" (String. ^bytes (second @received))))]))))

(defn- test-query-records
  [sys pet-store]
  (let [whiskers {:pet-id "pet-10"
                  :name "Whiskers"
                  :species "hamster"
                  :age-months 6}
        rex {:pet-id "pet-11" :name "Rex" :species "parrot" :age-months 48}
        record-db (system/instance sys [:fdb :record-db])]
    (testing "can query records by field value"
      (nom-test>
        [_
         (SUT/transact record-db
                       pet-store
                       "pets"
                       (fn [store]
                         (SUT/save-record store
                                          (test-schema/Pet->java whiskers))
                         (SUT/save-record store (test-schema/Pet->java rex))))
         results
         (SUT/transact record-db
                       pet-store
                       "pets"
                       (fn [store]
                         (SUT/query-records store "Pet" "species" "hamster")))
         _
         (is (= 1 (count results)))
         retrieved
         (error/nom-> (first results) test-schema/pb->Pet)
         _
         (is (= whiskers (utility/record->map retrieved)))]))))

(deftest kv-test
  (with-test-system [sys "classpath:fdb/application-test.yml"]
                    (test-str-kv sys)
                    (test-proto-kv sys)))

(deftest store-test
  (with-test-system [sys "classpath:fdb/application-test.yml"]
                    (let [pet-store (system/instance sys [:fdb :pet-store])]
                      (test-record-layer sys pet-store)
                      (test-query-records sys pet-store)
                      (test-record-layer-consumer sys pet-store))))

(deftest meta-store-test
  (with-test-system [sys "classpath:fdb/application-test.yml"]
                    (let [pet-store (system/instance sys
                                                     [:fdb :pet-meta-store])]
                      (test-record-layer sys pet-store)
                      (test-query-records sys pet-store)
                      (test-record-layer-consumer sys pet-store))))
