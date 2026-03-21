(ns com.repldriven.mono.error.interface-test
  (:require
    [com.repldriven.mono.error.interface :as error :refer [try-nom try-nom-ex]]
    [clojure.test :refer [deftest is testing]]))

(deftest fail-test
  (testing "map payload"
    (is (= [:error/anomaly :foo/bar {:message "x" :reason "y"}]
           (error/fail :foo/bar {:message "x" :reason "y"}))))
  (testing "string payload becomes :message"
    (is (= [:error/anomaly :foo/bar {:message "oops"}]
           (error/fail :foo/bar "oops"))))
  (testing "keyword-value pairs"
    (is (= [:error/anomaly :foo/bar {:a 1 :b 2}]
           (error/fail :foo/bar :a 1 :b 2))))
  (testing "no payload"
    (is (= [:error/anomaly :foo/bar {}] (error/fail :foo/bar)))))

(deftest anomaly?-test
  (is (error/anomaly? (error/fail :foo/bar {:message "x"})))
  (is (not (error/anomaly? {:message "x"})))
  (is (not (error/anomaly? nil)))
  (is (not (error/anomaly? "string"))))

(deftest kind-test
  (is (= :foo/bar (error/kind (error/fail :foo/bar {:message "x"}))))
  (is (nil? (error/kind "not an anomaly"))))

(deftest payload-test
  (is (= {:message "x" :reason "y"}
         (error/payload (error/fail :foo/bar {:message "x" :reason "y"}))))
  (is (nil? (error/payload "not an anomaly"))))

(deftest try-nom-test
  (testing "returns value on success"
    (is (= 42 (try-nom :foo/bar "failed" 42))))
  (testing "catches exception and returns anomaly"
    (let [result (try-nom :foo/bar "failed" (throw (ex-info "boom" {})))]
      (is (error/anomaly? result))
      (is (= :foo/bar (error/kind result)))
      (is (= "failed" (:message (error/payload result)))))))

(deftest try-nom-ex-test
  (testing "catches specified exception type"
    (let [result (try-nom-ex :foo/bar IllegalArgumentException
                             "bad arg" (throw (IllegalArgumentException.
                                               "boom")))]
      (is (error/anomaly? result))
      (is (= :foo/bar (error/kind result)))))
  (testing "does not catch other exception types"
    (is (thrown? RuntimeException
                 (try-nom-ex :foo/bar IllegalArgumentException
                             "bad arg" (throw (RuntimeException. "boom")))))))
