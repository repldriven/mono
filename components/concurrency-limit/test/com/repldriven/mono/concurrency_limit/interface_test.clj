(ns com.repldriven.mono.concurrency-limit.interface-test
  (:require
    [com.repldriven.mono.concurrency-limit.interface :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(def ^:private one-sample-windows {:window-size 1 :window-ms 0})

(defn- acquire-all
  [limiter]
  (doall (take-while some? (repeatedly (fn [] (SUT/try-acquire limiter))))))

(deftest limiter-test
  (testing "an integer is a fixed limit"
    (let [limiter (SUT/limiter 5)]
      (is (= 5 (SUT/limit limiter)))
      (is (= 5 (count (acquire-all limiter))))
      (is (= 5 (SUT/in-flight limiter)))
      (is (nil? (SUT/capacity limiter)))))
  (testing "a map with no options starts at 8"
    (is (= 8 (SUT/limit (SUT/limiter {})))))
  (testing "initial defaults to max where max is lower"
    (is (= 4 (SUT/limit (SUT/limiter {:max 4}))))))

(deftest invalid-options-test
  (doseq [[opts k] [[{:headroom 0.5} :headroom]
                    [{:frequency 2} :frequency]
                    [{:min 10 :initial 5} :min]
                    [{:initial 50 :max 10} :initial]
                    ["twenty" nil]]]
    (testing (str "refuses " (pr-str opts))
      (let [result (SUT/limiter opts)]
        (is (error/rejection? result))
        (is (= :concurrency-limit/invalid-options (error/kind result)))
        (is (= k (:key (error/payload result))))
        (is (string? (:message (error/payload result))))))))

(deftest release-test
  (testing "a released permit is granted again"
    (let [limiter (SUT/limiter 2)
          [permit] (acquire-all limiter)]
      (is (nil? (SUT/try-acquire limiter)))
      (is (nil? (SUT/release permit)))
      (is (some? (SUT/try-acquire limiter)))))
  (testing "a release that fills a window in which the limit bound steps it"
    (let [limiter (SUT/limiter (merge {:initial 1 :max 4} one-sample-windows))
          permit (SUT/try-acquire limiter)]
      (is (nil? (SUT/try-acquire limiter)))
      (SUT/release permit)
      (is (= 2 (SUT/limit limiter)))
      (is (pos? (SUT/capacity limiter)))))
  (testing "an ignored release records no sample and steps nothing"
    (let [limiter (SUT/limiter (merge {:initial 1 :max 4} one-sample-windows))
          permit (SUT/try-acquire limiter)]
      (is (nil? (SUT/try-acquire limiter)))
      (SUT/release permit :ignored)
      (is (= 1 (SUT/limit limiter)))
      (is (nil? (SUT/capacity limiter)))
      (is (zero? (SUT/in-flight limiter))))))

(deftest acquire-test
  (testing "acquire waits until another permit is released"
    (let [limiter (SUT/limiter 1)
          held (SUT/acquire limiter)
          waiting (future (SUT/acquire limiter))]
      (is (= ::waiting (deref waiting 100 ::waiting)))
      (SUT/release held)
      (is (map? (deref waiting 1000 ::timed-out)))
      (is (= 1 (SUT/in-flight limiter)))))
  (testing
    "a thread interrupted while it waits gets an anomaly, still interrupted"
    (let [limiter (SUT/limiter 1)
          _ (SUT/acquire limiter)
          outcome (promise)
          waiter (Thread. (fn []
                            (let [result (SUT/acquire limiter)]
                              (deliver outcome
                                       [result
                                        (.isInterrupted
                                         (Thread/currentThread))]))))]
      (.start waiter)
      (is (= ::waiting (deref outcome 100 ::waiting)))
      (.interrupt waiter)
      (let [[result interrupted?] (deref outcome 1000 [::timed-out])]
        (is (= :concurrency-limit/acquire (error/kind result)))
        (is (true? interrupted?))))))
