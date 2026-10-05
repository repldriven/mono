(ns com.repldriven.mono.message-bus.interface-test
  (:require
    [com.repldriven.mono.message-bus.interface :as SUT]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as util]
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

(defn- tracking-handler
  [ms]
  (let [seen (atom {})
        running (atom 0)
        peak (atom 0)]
    {:seen seen
     :peak peak
     :handler
     (fn [data]
       (swap! peak max (swap! running inc))
       (Thread/sleep (long ms))
       (swap! seen update (get data "k") (fnil conj []) (get data "seq"))
       (swap! running dec))}))

(defn- await-count
  [seen n]
  (let [deadline (+ (util/now) 10000)]
    (while (and (< (reduce + (map count (vals @seen))) n)
                (< (util/now) deadline))
      (Thread/sleep 20))))

(deftest performers-local-test
  (with-test-system
   [sys "classpath:message-bus/application-local-test.yml"]
   (let [bus (system/instance sys [:message-bus :bus])
         ks ["a" "b" "c" "d"]
         sends (for [i (range 10) k ks] {"k" k "seq" i})]
     (testing "messages sharing a key keep their order across performers"
       (let [{:keys [seen peak handler]} (tracking-handler 20)]
         (SUT/subscribe bus :keyed handler)
         (doseq [m sends] (SUT/send bus :keyed m {:key (get m "k")}))
         (await-count seen (count sends))
         (is (= (zipmap ks (repeat (vec (range 10)))) @seen)
             "each key's messages arrive in the order they were sent")
         (is (< 1 @peak) "different keys are handled at the same time")
         (SUT/unsubscribe bus :keyed)))
     (testing "a performer's thread is named for its channel"
       (let [thread-name (promise)]
         (SUT/subscribe
          bus
          :keyed
          (fn [_] (deliver thread-name (.getName (Thread/currentThread)))))
         (SUT/send bus :keyed {"k" "a" "seq" 0} {:key "a"})
         (is (re-matches #"keyed-performer-\d+"
                         (str (deref thread-name 5000 ::timeout))))
         (SUT/unsubscribe bus :keyed)))
     (testing "messages sent without a key spread across performers"
       (let [{:keys [seen peak handler]} (tracking-handler 20)]
         (SUT/subscribe bus :keyed handler)
         (doseq [m sends] (SUT/send bus :keyed m))
         (await-count seen (count sends))
         (is (= (count sends) (reduce + (map count (vals @seen)))))
         (is (< 1 @peak) "unkeyed messages are handled at the same time")
         (SUT/unsubscribe bus :keyed)))
     (testing "a narrower key orders by itself within one send key"
       (let [{:keys [seen peak handler]} (tracking-handler 20)]
         (SUT/subscribe bus :keyed handler {:key-fn (fn [data] (get data "k"))})
         (doseq [m sends] (SUT/send bus :keyed m {:key "one-tenant"}))
         (await-count seen (count sends))
         (is (= (zipmap ks (repeat (vec (range 10)))) @seen)
             "each narrower key keeps its order")
         (is (< 1 @peak)
             "one send key's messages run concurrently by the narrower key")
         (SUT/unsubscribe bus :keyed)))
     (testing "one performer handles a channel one message at a time"
       (let [{:keys [seen peak handler]} (tracking-handler 5)]
         (SUT/subscribe bus :command handler)
         (doseq [m sends] (SUT/send bus :command m {:key (get m "k")}))
         (await-count seen (count sends))
         (is (= 1 @peak))
         (SUT/unsubscribe bus :command))))))
