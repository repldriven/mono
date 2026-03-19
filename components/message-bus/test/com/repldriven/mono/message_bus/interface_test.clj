(ns com.repldriven.mono.message-bus.interface-test
  (:require
    [com.repldriven.mono.message-bus.interface :as SUT]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [clojure.test :refer [deftest is testing]]))

(def ^:private test-message
  {"id" "test-1"
   "command" "test-command"
   "correlation_id" "corr-1"
   "causation_id" nil
   "traceparent" nil
   "tracestate" nil
   "payload" nil
   "reply_to" nil})

(deftest message-bus-local-test
  (with-test-system
   [sys "classpath:message-bus/application-local-test.yml"]
   (let [bus (system/instance sys [:message-bus :bus])]
     (testing "Send and receive on a single channel"
       (let [received (promise)]
         (SUT/subscribe bus :command (fn [data] (deliver received data)))
         (nom-test> [_ (SUT/send bus :command test-message)])
         (let [data (deref received 5000 ::timeout)]
           (is (not= ::timeout data) "Should receive message within timeout")
           (when (not= ::timeout data)
             (is (= "test-1" (get data "id")))
             (is (= "test-command" (get data "command")))))
         (SUT/unsubscribe bus :command)))
     (testing "Send on one channel, receive reply on another"
       (let [received (promise)]
         (SUT/subscribe bus :reply (fn [data] (deliver received data)))
         (SUT/subscribe bus :command (fn [data] (SUT/send bus :reply data)))
         (nom-test> [_ (SUT/send bus :command test-message)])
         (let [data (deref received 5000 ::timeout)]
           (is (not= ::timeout data) "Should receive reply within timeout")
           (when (not= ::timeout data)
             (is (= "test-1" (get data "id")))
             (is (= "test-command" (get data "command")))))
         (SUT/unsubscribe bus :command)
         (SUT/unsubscribe bus :reply))))))
