(ns com.repldriven.mono.utility.uuid-test
  (:require
    [com.repldriven.mono.utility.uuid :as SUT]
    [clojure.test :refer [deftest is testing]]))

(deftest v7-test
  (testing "is a version 7 UUID" (is (= 7 (.version (SUT/v7)))))
  (testing "differs between calls" (is (not= (SUT/v7) (SUT/v7)))))

(deftest suffix-test
  (testing "is n lowercase hex characters"
    (is (re-matches #"[0-9a-f]{8}" (SUT/suffix 8)))
    (is (re-matches #"[0-9a-f]{12}" (SUT/suffix 12))))
  (testing "differs between calls in the same millisecond"
    (is (= 100 (count (into #{} (repeatedly 100 #(SUT/suffix 8))))))))
