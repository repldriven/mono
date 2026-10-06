(ns com.repldriven.mono.concurrency-limit.window-test
  (:require
    [com.repldriven.mono.concurrency-limit.window :as SUT]

    [clojure.test :refer [deftest is testing]]))

(def ^:private opts {:window-size 2 :window-ms 1})

(deftest full-test
  (testing "a window closes once it holds enough samples and time has passed"
    (let [w (-> (SUT/open 0)
                (SUT/sample 0 10)
                (SUT/sample 0 30))]
      (is (not (SUT/full? (SUT/sample (SUT/open 0) 0 10) 2000000 opts)))
      (is (not (SUT/full? w 500000 opts)))
      (is (SUT/full? w 1000000 opts)))))

(deftest close-test
  (testing "throughput is samples over the time open, latency their mean"
    (let [w (-> (SUT/open 0)
                (SUT/sample 0 10)
                (SUT/sample 0 30)
                SUT/bound)]
      (is (= {:throughput 0.002 :latency 20.0 :bound? true}
             (SUT/close w 1000)))))
  (testing
    "a sample admitted before the window opened counts in throughput only"
    (let [w (-> (SUT/open 100)
                (SUT/sample 40 150)
                (SUT/sample 120 140))]
      (is (= {:throughput 0.02 :latency 20.0 :bound? false}
             (SUT/close w 200)))))
  (testing "a window whose samples were all admitted before it has no latency"
    (is (nil? (:latency (SUT/close (SUT/sample (SUT/open 100) 40 150) 200))))))
