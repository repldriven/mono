(ns ^:eftest/synchronized com.repldriven.mono.bank-idv.interface-test
  (:require
    com.repldriven.mono.fdb.interface
    com.repldriven.mono.bank-idv.interface
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [clojure.test :refer [deftest is testing]]))

(def ^:private test-org-id "org_test_idv")

(defn- send-command
  [proc schemas command-name data]
  (let [payload (avro/serialize (get schemas command-name) data)]
    (if (error/anomaly? payload)
      payload
      (processor/process proc {:command command-name :payload payload}))))

(defn- decode-payload
  [schemas schema-name result]
  (avro/deserialize-same (get schemas schema-name) (:payload result)))

(defn- poll-status
  "Polls IDV until status matches expected, or times out
  after 5 s."
  [proc schemas verification-id expected]
  (loop [attempts 50]
    (let [result (send-command proc
                               schemas
                               "get-idv"
                               {:organization-id test-org-id
                                :verification-id verification-id})
          decoded (when (= "ACCEPTED" (:status result))
                    (decode-payload schemas "idv" result))]
      (cond (= expected (:status decoded))
            decoded
            (pos? attempts)
            (do (Thread/sleep 100) (recur (dec attempts)))
            :else
            decoded))))

(defn- test-initiate-idv
  [proc schemas]
  (testing "initiate creates IDV with pending status"
    (let [payload {:organization-id test-org-id :party-id "pty.test-party-id"}]
      (nom-test> [result (send-command proc schemas "initiate-idv" payload)
                  _
                  (is (= "ACCEPTED" (:status result)))
                  decoded
                  (decode-payload schemas "idv" result)
                  _
                  (is (some? (:verification-id decoded)))
                  _
                  (is (= "pty.test-party-id" (:party-id decoded)))
                  _
                  (is (= :idv-status-pending (:status decoded)))
                  _
                  (is (nil? (:completed-at decoded)))]))))

(defn- test-watcher-transitions
  [proc schemas]
  (testing "watcher transitions pending->accepted"
    (let [payload {:organization-id test-org-id :party-id "pty.watcher-test"}]
      (nom-test> [result (send-command proc schemas "initiate-idv" payload)
                  _
                  (is (= "ACCEPTED" (:status result)))
                  decoded
                  (decode-payload schemas "idv" result)
                  verification-id
                  (:verification-id decoded)
                  polled
                  (poll-status proc
                               schemas
                               verification-id
                               :idv-status-accepted)
                  _
                  (is (= :idv-status-accepted (:status polled)))]))))

(defn- test-unknown-command
  [proc schemas]
  (testing "unknown command returns rejection"
    (let [result (send-command proc
                               schemas
                               "unknown-idv-command"
                               {:organization-id test-org-id
                                :party-id "pty.x"})]
      (is (error/rejection? result))
      (is (= :bank-idv/unknown-command (error/kind result))))))

(deftest process-idv-test
  (with-test-system [sys "classpath:bank-idv/application-test.yml"]
                    (let [proc (system/instance sys [:idv :processor])
                          schemas (system/instance sys [:avro :serde])]
                      (test-initiate-idv proc schemas)
                      (test-watcher-transitions proc schemas)
                      (test-unknown-command proc schemas))))
