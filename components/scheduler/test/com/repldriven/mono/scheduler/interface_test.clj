(ns com.repldriven.mono.scheduler.interface-test
  (:require
    [com.repldriven.mono.scheduler.interface :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(defn- wait-until
  "Poll `pred` up to `tries` × 100ms; return its value (truthy) or nil."
  [pred tries]
  (loop [n 0]
    (let [v (pred)]
      (cond v
            v
            (>= n tries)
            nil
            :else
            (do (Thread/sleep 100) (recur (inc n)))))))

(deftest schedule-and-unschedule-test
  (let [sched (SUT/start)
        hits (atom 0)]
    (try (testing "a scheduled job fires its handler"
           ;; every-second cron
           (SUT/schedule sched "tick" "* * * * * ?" (fn [] (swap! hits inc)))
           (is (wait-until #(pos? @hits) 30) "handler fired within ~3s"))
         (testing "unschedule stops further fires"
           (SUT/unschedule sched "tick")
           (let [after @hits]
             (Thread/sleep 1500)
             (is (= after @hits) "no further fires after unschedule")))
         (finally (SUT/stop sched)))))

(deftest bad-cron-is-an-anomaly-test
  (testing "a malformed expression is an anomaly, not a ParseException"
    (doseq [expr ["not a cron" "" "* * * * * *"]]
      (let [result (SUT/next-fire-at expr 0)]
        (is (error/anomaly? result)
            (str "expected an anomaly for " (pr-str expr)))
        (is (= :scheduler/next-fire-at (error/kind result))))))
  (testing "so is nil" (is (error/anomaly? (SUT/next-fire-at nil 0))))
  (testing "and scheduling with one does not throw either"
    (let [sched (SUT/start)]
      (try (let [result (SUT/schedule sched "bad" "not a cron" (fn []))]
             (is (error/anomaly? result))
             (is (= :scheduler/schedule (error/kind result))))
           (finally (SUT/stop sched)))))
  (testing "a valid expression still returns millis"
    (let [at (SUT/next-fire-at "0 0 12 * * ?" 0)]
      (is (int? at))
      (is (pos? at)))))
