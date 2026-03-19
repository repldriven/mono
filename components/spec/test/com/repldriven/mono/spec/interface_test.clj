(ns com.repldriven.mono.spec.interface-test
  (:require
    [com.repldriven.mono.spec.interface :as SUT]
    [malli.core :as m]
    [clojure.test :refer [deftest is testing]]))

(deftest non-empty-string-test
  (testing "A non-empty string should meet the spec"
    (is (m/validate (m/schema SUT/non-empty-string?) "a"))))
