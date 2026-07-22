(ns com.repldriven.mono.utility.collections-test
  (:require
    [com.repldriven.mono.utility.collections :as SUT]
    [clojure.test :refer [deftest is testing]]))

(deftest deep-merge-test
  (testing "Recursively merge maps"
    (is (= {:a {:b 2 :c 3}} (SUT/deep-merge {:a {:b 1}} {:a {:b 2 :c 3}})))
    (is (= {:x 1 :y {:z 2}} (SUT/deep-merge {:x 1} {:y {:z 2}}))))
  (testing "Last value wins for non-maps"
    (is (= 3 (SUT/deep-merge 1 2 3)))
    (is (= "bar" (SUT/deep-merge "foo" "bar")))))

(deftest val-strs->keywords-test
  (testing "Converts string values to keywords"
    (is (= {:a :hello :b :world}
           (SUT/val-strs->keywords {:a "hello" :b "world"}))))
  (testing "Leaves non-string values unchanged"
    (is (= {:a 1 :b true :c :already}
           (SUT/val-strs->keywords {:a 1 :b true :c :already}))))
  (testing "Converts recursively"
    (is (= {:a {:b :nested :c 42}}
           (SUT/val-strs->keywords {:a {:b "nested" :c 42}}))))
  (testing "Handles vectors of maps"
    (is (= [{:x :foo} {:y :bar}]
           (SUT/val-strs->keywords [{:x "foo"} {:y "bar"}]))))
  (testing "Handles empty and nil"
    (is (= {} (SUT/val-strs->keywords {})))
    (is (= nil (SUT/val-strs->keywords nil)))))

(deftest keys->strs-test
  (testing "Convert map keys to strings recursively"
    (is (= {"a" 1 "b" 2} (SUT/keys->strs {:a 1 :b 2})))
    (is (= {"nested" {"c" 3}} (SUT/keys->strs {:nested {:c 3}})))
    (is (= [{"a" 1} {"b" 2}] (SUT/keys->strs [{:a 1} {:b 2}])))))
