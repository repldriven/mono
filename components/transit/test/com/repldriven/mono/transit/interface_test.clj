(ns com.repldriven.mono.transit.interface-test
  (:require
    [com.repldriven.mono.transit.interface :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]])
  (:import
    (java.io ByteArrayInputStream ByteArrayOutputStream)
    (java.time Instant)
    (java.util Date UUID)))

(def ^:private edn
  {:account/id (UUID/fromString "0190a1f4-3846-7c9a-bd3a-a5a2948a4d6a")
   :account/status :account-status/open
   :account/tags #{:a :b}
   :account/history '(1 2 3)
   :account/owners [{:party/id "p1"} {:party/id "p2"}]
   :account/opened (Date. 0)
   :account/balance 12345678901234567890N
   :account/rate 1/3
   :account/rule 'account/rule
   "string-key" {[1 2] :compound-key}})

(deftest round-trip-test
  (testing "a string round-trips EDN"
    (let [s (SUT/write-str edn)]
      (is (string? s))
      (is (= edn (SUT/read-str s)))))
  (testing "a stream round-trips EDN"
    (let [out (ByteArrayOutputStream.)]
      (is (not (error/anomaly? (SUT/write edn out))))
      (is (= edn (SUT/read (ByteArrayInputStream. (.toByteArray out))))))))

(deftest encoding-test
  (testing "writes compact transit+json"
    (is (= "[\"^ \",\"~:a/k\",1,\"~:a/n\",[\"^ \",\"^0\",2]]"
           (SUT/write-str {:a/k 1 :a/n {:a/k 2}}))))
  (testing "reads verbose transit+json"
    (is (= {:a/k #{1}} (SUT/read-str "{\"~:a/k\":{\"~#set\":[1]}}"))))
  (testing "reads an unknown tag as a tagged value"
    (let [result (SUT/read-str "[\"~#point\",[1,2]]")]
      (is (not (error/anomaly? result)))
      (is (= [1 2] (.getRep result))))))

(deftest anomaly-test
  (testing "a value with no handler is a serialize anomaly"
    (is (= :transit/serialize
           (error/kind (SUT/write-str {:at (Instant/ofEpochMilli 0)})))))
  (testing "malformed input is a parse anomaly"
    (is (= :transit/parse (error/kind (SUT/read-str "[\"^ \","))))
    (is (= :transit/parse (error/kind (SUT/read-str ""))))
    (is (= :transit/parse (error/kind (SUT/read-str nil))))))
