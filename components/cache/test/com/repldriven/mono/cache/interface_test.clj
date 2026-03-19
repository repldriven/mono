(ns com.repldriven.mono.cache.interface-test
  (:require
    [com.repldriven.mono.cache.interface :as SUT]
    [clojure.test :refer [deftest is testing]]))

(deftest create-cache-test
  (testing "creates an atom-backed TTL cache"
    (let [c (SUT/create 60000)] (is (instance? clojure.lang.Atom c)))))

(deftest lookup-test
  (testing "caches and returns value from miss-fn"
    (let [c (SUT/create 60000)
          call-count (atom 0)
          miss-fn #(do (swap! call-count inc) "value")]
      (is (= "value" (SUT/lookup c :k miss-fn)))
      (is (= "value" (SUT/lookup c :k miss-fn)))
      (is (= 1 @call-count))))
  (testing "does not cache nil results"
    (let [c (SUT/create 60000)
          call-count (atom 0)
          miss-fn #(do (swap! call-count inc) nil)]
      (is (nil? (SUT/lookup c :k miss-fn)))
      (is (nil? (SUT/lookup c :k miss-fn)))
      (is (= 2 @call-count)))))

(deftest evict-test
  (testing "removes entry from cache"
    (let [c (SUT/create 60000)
          call-count (atom 0)
          miss-fn #(do (swap! call-count inc) "value")]
      (SUT/lookup c :k miss-fn)
      (SUT/evict c :k)
      (SUT/lookup c :k miss-fn)
      (is (= 2 @call-count)))))
