(ns ^:eftest/synchronized com.repldriven.mono.bank-party.interface-test
  (:require
    com.repldriven.mono.bank-party.interface
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.bank-schema.interface :as schema]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [clojure.test :refer [deftest is testing]]))

(def ^:private test-org-id "org_test_party")

(defn- send-command
  [proc schemas command-name data]
  (let [payload (avro/serialize (get schemas command-name) data)]
    (if (error/anomaly? payload)
      payload
      (processor/process proc {:command command-name :payload payload}))))

(defn- decode-payload
  [schemas schema-name result]
  (avro/deserialize-same (get schemas schema-name) (:payload result)))

(defn- seed-accepted-idv
  "Seeds an accepted IDV record and writes a changelog entry
  to trigger the party watcher."
  [record-db store-fn party-id]
  (fdb/transact record-db
                store-fn
                "idvs"
                (fn [store]
                  (let [verification-id (str "iv-test-" party-id)
                        idv {:organization-id test-org-id
                             :verification-id verification-id
                             :party-id party-id
                             :status :idv-status-accepted
                             :created-at (System/currentTimeMillis)
                             :updated-at (System/currentTimeMillis)}]
                    (fdb/save-record store (schema/Idv->java idv))
                    (fdb/write-changelog store
                                         "idvs"
                                         verification-id
                                         (schema/IdvChangelog->pb
                                          {:organization-id test-org-id
                                           :verification-id verification-id
                                           :status-before :idv-status-pending
                                           :status-after
                                           :idv-status-accepted}))))))

(defn- load-party
  "Loads a party record by id."
  [record-db store-fn party-id]
  (fdb/transact record-db
                store-fn
                "parties"
                (fn [store]
                  (when-let [rec (fdb/load-record store test-org-id party-id)]
                    (schema/pb->Party rec)))))

(defn- poll-party-status
  "Polls party until status matches expected, or times out
  after 5 s."
  [record-db store-fn party-id expected]
  (loop [attempts 50]
    (let [party (load-party record-db store-fn party-id)]
      (cond (= expected (:status party))
            party
            (pos? attempts)
            (do (Thread/sleep 100) (recur (dec attempts)))
            :else
            party))))

(defn- test-create-party
  [proc schemas]
  (testing "create-party creates party with pending status"
    (let [create-payload {:organization-id test-org-id
                          :type :party-type-person
                          :display-name "Jane Doe"
                          :given-name "Jane"
                          :family-name "Doe"
                          :date-of-birth 19900115
                          :nationality "GB"
                          :national-identifier
                          {:type :identifier-type-national-insurance
                           :value "AB123456C"
                           :issuing-country "GB"}}]
      (nom-test>
        [result (send-command proc schemas "create-party" create-payload)
         _
         (is (= "ACCEPTED" (:status result)))
         decoded
         (decode-payload schemas "party" result)
         _
         (is (some? (:party-id decoded)))
         _ (is (= :party-status-pending (:status decoded)))
         _ (is (= :party-type-person (:type decoded)))
         _
         (is (= "Jane Doe" (:display-name decoded)))]))))

(defn- test-duplicate-national-identifier
  [proc schemas]
  (testing "duplicate national identifier returns rejection"
    (let [ni {:type :identifier-type-national-insurance
              :value "ZZ999999D"
              :issuing-country "GB"}
          payload {:organization-id test-org-id
                   :type :party-type-person
                   :display-name "First"
                   :given-name "First"
                   :family-name "Person"
                   :date-of-birth 19900101
                   :nationality "GB"
                   :national-identifier ni}]
      (nom-test> [result (send-command proc schemas "create-party" payload)
                  _
                  (is (= "ACCEPTED" (:status result)))])
      (let [result (send-command proc
                                 schemas
                                 "create-party"
                                 (assoc payload
                                        :display-name "Second"
                                        :given-name "Second"))]
        (is (error/rejection? result))
        (is (= :bank-party/duplicate-national-identifier
               (error/kind result)))))))

(defn- test-watcher-transitions
  [proc schemas record-db store-fn]
  (testing "watcher transitions party pending->active on accepted IDV"
    (let [create-payload {:organization-id test-org-id
                          :type :party-type-person
                          :display-name "Watcher Test"
                          :given-name "Watch"
                          :family-name "Test"
                          :date-of-birth 19850101
                          :nationality "GB"}]
      (nom-test>
        [result (send-command proc schemas "create-party" create-payload)
         _
         (is (= "ACCEPTED" (:status result)))
         decoded
         (decode-payload schemas "party" result)
         party-id (:party-id decoded)
         _
         (seed-accepted-idv record-db store-fn party-id)
         polled
         (poll-party-status record-db store-fn party-id :party-status-active)
         _
         (is (= :party-status-active (:status polled)))]))))

(defn- test-unknown-command
  [proc schemas]
  (testing "unknown command returns rejection"
    (let [result (send-command proc
                               schemas
                               "unknown-party-command"
                               {:organization-id test-org-id
                                :type :party-type-person
                                :display-name "X"
                                :given-name "X"
                                :family-name "Y"
                                :date-of-birth 20000101
                                :nationality "US"})]
      (is (error/rejection? result))
      (is (= :bank-party/unknown-command (error/kind result))))))

(deftest process-party-test
  (with-test-system [sys "classpath:bank-party/application-test.yml"]
                    (let [proc (system/instance sys [:party :processor])
                          schemas (system/instance sys [:avro :serde])
                          record-db (system/instance sys [:fdb :record-db])
                          store-fn (system/instance sys [:fdb :store])]
                      (test-create-party proc schemas)
                      (test-duplicate-national-identifier proc schemas)
                      (test-watcher-transitions proc schemas record-db store-fn)
                      (test-unknown-command proc schemas))))
