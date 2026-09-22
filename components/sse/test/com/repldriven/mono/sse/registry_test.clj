(ns com.repldriven.mono.sse.registry-test
  (:require
    [com.repldriven.mono.sse.registry :as SUT]

    [clojure.test :refer [deftest is testing]]))

(deftest registry-test
  (testing "an event reaches every stream open on its topic, and no other"
    (let [registry (SUT/registry)
          a (SUT/subscribe! registry "a")
          a' (SUT/subscribe! registry "a")
          b (SUT/subscribe! registry "b")]
      (SUT/publish! registry "a" {:id 1})
      (is (= {:id 1} (SUT/next! a 100)))
      (is (= {:id 1} (SUT/next! a' 100)))
      (is (nil? (SUT/next! b 10)))
      (is (= 2 (SUT/open-count registry "a")))))
  (testing "an event published with no stream open is not kept"
    (let [registry (SUT/registry)]
      (SUT/publish! registry "a" {:id 1})
      (is (nil? (SUT/next! (SUT/subscribe! registry "a") 10)))))
  (testing "a stream unsubscribed is no longer counted, and its topic goes"
    (let [registry (SUT/registry)
          a (SUT/subscribe! registry "a")]
      (SUT/unsubscribe! registry a)
      (is (zero? (SUT/open-count registry "a")))
      (is (= {} @(:subscribers registry)))))
  (testing "closing hands every open stream, and every later one, closed"
    (let [registry (SUT/registry)
          a (SUT/subscribe! registry "a")]
      (SUT/close! registry)
      (is (= SUT/closed (SUT/next! a 100)))
      (is (= SUT/closed (SUT/next! (SUT/subscribe! registry "b") 100))))))
