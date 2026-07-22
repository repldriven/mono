(ns com.repldriven.mono.encryption.interface-test
  (:require
    [com.repldriven.mono.encryption.interface :as SUT]
    [clojure.test :refer [deftest is testing]]))

(deftest generate-token-test
  (testing "generates prefixed token"
    (let [key (SUT/generate-token "sk_live_")]
      (is (string? key))
      (is (.startsWith key "sk_live_")))))

(deftest hash-token-test
  (testing "returns consistent hex SHA-256 hash"
    (let [key "sk_live_test123"
          h1 (SUT/hash-token key)
          h2 (SUT/hash-token key)]
      (is (string? h1))
      (is (= 64 (count h1)))
      (is (= h1 h2)))))

(deftest create-key-pair-test
  (testing "generates RSA 512-bit key pair"
    (let [kp (SUT/create-key-pair {:algorithm "RSA" :key-size 512})]
      (is (some? (:private-key kp)))
      (is (some? (:public-key kp)))
      (is (= "RSA" (:algorithm kp))))))
