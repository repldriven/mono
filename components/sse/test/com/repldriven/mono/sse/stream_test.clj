(ns com.repldriven.mono.sse.stream-test
  (:require
    [com.repldriven.mono.sse.registry :as registry]
    [com.repldriven.mono.sse.stream :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(def ^:private keep-alive-ms 50)

(defn- wait-for
  [pred]
  (loop [tries 100]
    (cond (pred)
          true
          (zero? tries)
          false
          :else
          (do (Thread/sleep 20) (recur (dec tries))))))

(defn- open
  "Serve a stream on a future, recording what is emitted and what is
  told sent, and wait until it is subscribed."
  [registry topic opts]
  (let [emitted (atom [])
        sent (atom [])
        stream (future (SUT/serve registry
                                  topic
                                  (fn [event] (swap! emitted conj event) nil)
                                  (assoc opts
                                         :keep-alive-ms keep-alive-ms
                                         :on-sent (fn [event]
                                                    (swap! sent conj event)))))]
    (wait-for (fn [] (= 1 (registry/open-count registry topic))))
    {:emitted emitted :sent sent :stream stream}))

(deftest serve-test
  (testing "nil once subscribed, then pending, then what is published"
    (let [registry (registry/registry)
          {:keys [emitted sent stream]}
          (open registry "a" {:pending (fn [_] [{:id 1} {:id 2}])})]
      (registry/publish! registry "a" {:id 3})
      (is (wait-for (fn [] (some #{{:id 3}} @emitted))))
      (registry/close! registry)
      (is (nil? (deref stream 1000 ::running)))
      (is (nil? (first @emitted)))
      (is (= [{:id 1} {:id 2} {:id 3}] (remove nil? @emitted)))
      (is (= [{:id 1} {:id 2} {:id 3}] @sent))
      (is (zero? (registry/open-count registry "a")))))
  (testing "an event already sent from pending is not sent again"
    (let [registry (registry/registry)
          {:keys [emitted stream]}
          (open registry "a" {:pending (fn [_] [{:id 1}])})]
      (registry/publish! registry "a" {:id 1})
      (registry/publish! registry "a" {:id 2})
      (is (wait-for (fn [] (some #{{:id 2}} @emitted))))
      (registry/close! registry)
      (deref stream 1000 ::running)
      (is (= [{:id 1} {:id 2}] (remove nil? @emitted)))))
  (testing "a keep-alive is nil, emitted whenever the wait passes"
    (let [registry (registry/registry)
          {:keys [emitted stream]} (open registry "a" {})]
      (is (wait-for (fn [] (<= 3 (count @emitted)))))
      (registry/close! registry)
      (deref stream 1000 ::running)
      (is (every? nil? @emitted))))
  (testing "pending is handed the last event id"
    (let [registry (registry/registry)
          given (promise)
          {:keys [stream]} (open registry
                                 "a"
                                 {:last-event-id "7"
                                  :pending (fn [id] (deliver given id) [])})]
      (registry/close! registry)
      (deref stream 1000 ::running)
      (is (= "7" (deref given 1000 ::none)))))
  (testing "an anomaly from pending ends the stream with it"
    (let [registry (registry/registry)
          refused (error/fail :test/pending "no")
          result (SUT/serve registry
                            "a"
                            (constantly nil)
                            {:pending (constantly refused)})]
      (is (= refused result))
      (is (zero? (registry/open-count registry "a")))))
  (testing "an anomaly from emit ends the stream, the event not told sent"
    (let [registry (registry/registry)
          gone (error/fail :sse/closed "gone")
          sent (atom [])
          result (SUT/serve registry
                            "a"
                            (fn [event] (when event gone))
                            {:pending (constantly [{:id 1}])
                             :on-sent (fn [event] (swap! sent conj event))})]
      (is (= gone result))
      (is (= [] @sent))
      (is (zero? (registry/open-count registry "a")))))
  (testing "what emit throws is thrown, and the stream is still let go"
    (let [registry (registry/registry)
          ;; nosemgrep: no-raw-throw -- the throw is the exit
          leave (fn [_] (throw (ex-info "left" {})))]
      (is (thrown? clojure.lang.ExceptionInfo
                   (SUT/serve registry "a" leave {})))
      (is (zero? (registry/open-count registry "a"))))))
