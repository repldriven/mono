(ns com.repldriven.mono.utility.id-test
  (:require
    [com.repldriven.mono.utility.id :as SUT]
    [clojure.test :refer [deftest is testing]]))

(def ^:private ulid-re #"^[a-z]+\.[0-9a-hjkmnp-tv-z]{26}$")

(deftest generate-test
  (testing "produces a prefixed lowercase ULID"
    (let [id (SUT/generate "acc")]
      (is (string? id))
      (is (re-matches ulid-re id))
      (is (.startsWith id "acc."))))
  (testing "successive calls are monotonic"
    (let [a (SUT/generate "pmt")
          b (SUT/generate "pmt")]
      (is (neg? (compare a b))))))
