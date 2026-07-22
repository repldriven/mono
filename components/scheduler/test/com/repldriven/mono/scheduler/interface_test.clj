(ns com.repldriven.mono.scheduler.interface-test
  (:require
    [com.repldriven.mono.scheduler.interface :as SUT]

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
           (SUT/schedule! sched "tick" "* * * * * ?" (fn [] (swap! hits inc)))
           (is (wait-until #(pos? @hits) 30) "handler fired within ~3s"))
         (testing "unschedule stops further fires"
           (SUT/unschedule! sched "tick")
           (let [after @hits]
             (Thread/sleep 1500)
             (is (= after @hits) "no further fires after unschedule")))
         (finally (SUT/stop sched)))))
