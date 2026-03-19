(ns com.repldriven.mono.external-test-runner.core-test
  (:require
    com.repldriven.mono.external-test-runner.main
    [clojure.test :refer [deftest is]]))

(deftest dummy-test (is (= 1 1)))
