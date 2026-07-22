(ns com.repldriven.mono.utility.time-test
  (:require
    [com.repldriven.mono.utility.time :as SUT]
    [clojure.test :refer [deftest is testing]]))

(deftest epoch-day->iso-date-test
  (testing "epoch-day renders as an ISO-8601 calendar date"
    (is (= "2026-06-18" (SUT/epoch-day->iso-date 20622)))
    (is (= "1970-01-01" (SUT/epoch-day->iso-date 0))))
  (testing "round-trips with today"
    (is (string? (SUT/epoch-day->iso-date (SUT/today))))))
