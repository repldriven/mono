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
     (testing "Send with opts behaves exactly as send without them"
       ;; The local bus has no partitions to key, so a keyed send is
       ;; delivered like any other rather than rejected.
       (let [received (promise)]
         (SUT/subscribe bus :command (fn [data] (deliver received data)))
         (nom-test> [_ (SUT/send bus
                                 :command
                                 (assoc test-message "id" "keyed-1")
                                 {:key "some-entity"})])
         (let [data (deref received 5000 ::timeout)]
           (is (not= ::timeout data) "Should receive message within timeout")
           (when (not= ::timeout data) (is (= "keyed-1" (get data "id")))))
         (SUT/unsubscribe bus :command)))
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
         (SUT/unsubscribe bus :command)))
     (testing "Every subscriber on a channel receives every message"
       ;; Taking straight from the channel would make these two compete,
       ;; so whichever loop won a message would be the only one to see it.
       (let [first-seen (promise)
             second-seen (promise)]
         (SUT/subscribe bus :command (fn [data] (deliver first-seen data)))
         (SUT/subscribe bus :command (fn [data] (deliver second-seen data)))
         (nom-test> [_ (SUT/send bus
                                 :command
                                 (assoc test-message "id" "fan-out-1"))])
         (doseq [[label p] [["first" first-seen] ["second" second-seen]]]
           (let [data (deref p 5000 ::timeout)]
             (is (not= ::timeout data) (str label " subscriber saw nothing"))
             (when (not= ::timeout data) (is (= "fan-out-1" (get data "id"))))))
         (SUT/unsubscribe bus :command)))
     (testing "Unsubscribing one subscription leaves the others running"
       (let [stopped (atom [])
             kept (promise)
             going
             (SUT/subscribe bus :command (fn [data] (swap! stopped conj data)))]
         (SUT/subscribe bus :command (fn [data] (deliver kept data)))
         (SUT/unsubscribe bus :command going)
         (nom-test> [_ (SUT/send bus
                                 :command
                                 (assoc test-message "id" "one-left"))])
         (let [data (deref kept 5000 ::timeout)]
           (is (not= ::timeout data) "the other subscriber must still run")
           (when (not= ::timeout data) (is (= "one-left" (get data "id")))))
         (is (empty? @stopped) "the stopped subscriber must see nothing")
         (SUT/unsubscribe bus :command)))
     (testing "Unsubscribe stops every subscriber on the channel"
       (let [delivered (atom [])]
         (SUT/subscribe bus :command (fn [data] (swap! delivered conj data)))
         (SUT/subscribe bus :command (fn [data] (swap! delivered conj data)))
         (SUT/unsubscribe bus :command)
         (nom-test> [_ (SUT/send bus
                                 :command
                                 (assoc test-message "id" "after-stop"))])
         (Thread/sleep 200)
         (is (empty? @delivered)
             "a stopped subscriber must not keep taking messages"))))))
