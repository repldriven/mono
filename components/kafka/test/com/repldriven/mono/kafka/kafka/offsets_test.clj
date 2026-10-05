(ns com.repldriven.mono.kafka.kafka.offsets-test
  (:require
    [com.repldriven.mono.kafka.kafka.offsets :as SUT]

    [clojure.test :refer [deftest is testing]]))

(def ^:private tp ["topic" 0])

(defn- out
  [state & offsets]
  (reduce (fn [s o] (SUT/handed-out s tp o)) state offsets))

(defn- done
  [state & offsets]
  (reduce (fn [s o] (SUT/finished s tp o)) state offsets))

(deftest position-test
  (testing "nothing handed out commits nothing" (is (= {} (SUT/to-commit {}))))
  (testing "finished in order, the position is one past the last"
    (is (= {tp 3}
           (SUT/to-commit (-> {}
                              (out 0 1 2)
                              (done 0 1 2))))))
  (testing "finished out of order, the position waits at the lowest open"
    (is (= {tp 1}
           (SUT/to-commit (-> {}
                              (out 0 1 2 3)
                              (done 0 2 3)))))
    (is (= {tp 4}
           (SUT/to-commit (-> {}
                              (out 0 1 2 3)
                              (done 0 2 3 1))))))
  (testing "a position already committed is not committed again"
    (let [state (-> {}
                    (out 0 1)
                    (done 0))
          state (SUT/committed state (SUT/to-commit state))]
      (is (= {} (SUT/to-commit state)))
      (is (= {tp 2} (SUT/to-commit (done state 1)))))))

(deftest redelivery-test
  (testing "an offset waiting for redelivery holds the position"
    (let [state (-> {}
                    (out 5 6 7)
                    (SUT/waiting tp 5)
                    (done 6 7))]
      (is (= 5 (SUT/earliest-waiting state tp)))
      (is (= {tp 5} (SUT/to-commit state)))))
  (testing "handed out again, it is counted once, and finishing it moves on"
    (let [state (-> {}
                    (out 5 6)
                    (SUT/waiting tp 5)
                    (done 6)
                    (out 5))]
      (is (nil? (SUT/earliest-waiting state tp)))
      (is (= {tp 7} (SUT/to-commit (done state 5))))))
  (testing "a message handed out twice finishes only when both copies do"
    (let [state (-> {}
                    (out 5 6)
                    (SUT/waiting tp 5)
                    (out 5 6))]
      (is (= {tp 6} (SUT/to-commit (done state 6 5))))
      (is (= {tp 7} (SUT/to-commit (done state 6 5 6))))))
  (testing "the earliest of several waiting offsets is where to seek"
    (let [state (-> {}
                    (out 3 4 5)
                    (SUT/waiting tp 5)
                    (SUT/waiting tp 3))]
      (is (= 3 (SUT/earliest-waiting state tp))))))

(deftest rebalance-test
  (testing "a partition no longer assigned is dropped"
    (let [other ["topic" 1]
          state (-> {}
                    (out 0)
                    (SUT/handed-out other 9))]
      (is (= #{tp} (set (keys (SUT/retain state #{tp})))))
      (is (= {tp 0} (SUT/to-commit (SUT/retain state #{tp}))))))
  (testing "an ack for an offset not handed out changes nothing"
    (is (= {} (SUT/finished {} tp 4)))))
