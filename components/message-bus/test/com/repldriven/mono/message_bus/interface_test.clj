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
         (SUT/unsubscribe bus :reply)))
     (testing "A handler throw does not wedge the channel loop"
       (let [calls (atom 0)
             received (promise)]
         (SUT/subscribe bus
                        :command
                        (fn [data]
                          (if (= 1 (swap! calls inc))
                            ;; nosemgrep: no-raw-throw
                            (throw (ex-info "boom" {}))
                            (deliver received data))))
         (nom-test> [_ (SUT/send bus
                                 :command
                                 (assoc test-message "id" "throw-1"))])
         (nom-test> [_ (SUT/send bus :command (assoc test-message "id" "ok-2"))])
         (let [data (deref received 5000 ::timeout)]
           (is (not= ::timeout data)
               "second message must still be delivered after the first throws")
           (when (not= ::timeout data) (is (= "ok-2" (get data "id")))))
         (SUT/unsubscribe bus :command))))))
