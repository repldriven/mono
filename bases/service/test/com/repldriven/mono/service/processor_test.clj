(ns ^:eftest/synchronized com.repldriven.mono.service.processor-test
  (:require
    com.repldriven.mono.command-processor.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.test-schema.interface
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.command.interface :as command]
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
  (let [dispatcher (system/instance sys [:pets :dispatcher])
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

(deftest process-command-test
  (testing "Commands sent are processed and replied to via message-bus"
    (with-test-system
     [sys "classpath:service/application-test.yml"]
     (telemetry/with-span-tests
      [_ ["send-command" "process-command"]]
      (let [schemas (system/instance sys [:avro :serde])]
        (nom-test> [result (send-command
                            sys
                            "create-pet"
                            {:name "Whiskers" :species "cat" :age-months 24})
                    _ (is (= "ACCEPTED" (:status result)))
                    decoded (avro/deserialize-same (get schemas "pet")
                                                   (:payload result))
                    _ (is (some? (:pet-id decoded)))
                    _ (is (= "Whiskers" (:name decoded)))
                    _ (is (= "cat" (:species decoded)))]))))))
