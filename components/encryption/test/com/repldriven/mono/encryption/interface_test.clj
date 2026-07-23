(ns com.repldriven.mono.encryption.interface-test
  (:require
    [com.repldriven.mono.encryption.interface :as SUT]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.test-system.interface :refer [nom-test>]]

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

(deftest failures-are-anomalies-test
  (testing "malformed encoded keys come back as anomalies, not throws"
    (let [garbage (byte-array [1 2 3])]
      (nom-test> [_ (is (error/anomaly? (SUT/private-key-pkcs8-encoded->rsa
                                         garbage)))
                  _ (is (error/anomaly? (SUT/public-key-x509-encoded->rsa
                                         garbage)))
                  _ (is (= :encryption/private-key
                           (error/kind (SUT/private-key-pkcs8-encoded->rsa
                                        garbage))))])))
  (testing "nil where bytes were expected is an anomaly too"
    (is (error/anomaly? (SUT/private-key-pkcs8-encoded->rsa nil)))
    (is (error/anomaly? (SUT/public-key->der-string nil))))
  (testing "an unsupported algorithm says what is supported"
    (let [result (SUT/create-key-pair {:algorithm "DSA" :key-size 2048})
          payload (error/payload result)]
      (is (error/anomaly? result))
      (is (= :encryption/create-key-pair (error/kind result)))
      (is (= {:algorithm "DSA" :key-size 2048} (:requested payload)))
      (is (= {:algorithm "RSA" :key-size 512} (:supported payload)))))
  (testing "hashing a nil token is an anomaly"
    (is (error/anomaly? (SUT/hash-token nil)))))

(deftest round-trip-test
  (testing "a generated key pair survives DER encoding and decoding"
    (nom-test> [kp (SUT/create-key-pair {:algorithm "RSA" :key-size 512})
                der (SUT/public-key->der-string (:public-key kp))
                _ (is (string? der))
                decoded (SUT/public-key-x509-encoded->rsa
                         (.decode (java.util.Base64/getDecoder) der))
                _ (is (= (:public-key kp) decoded))])))

(deftest bytes-equals-test
  (testing "constant-time comparison, nil-safe"
    (is (SUT/bytes-equals? (.getBytes "a") (.getBytes "a")))
    (is (not (SUT/bytes-equals? (.getBytes "a") (.getBytes "b"))))
    (is (not (SUT/bytes-equals? (.getBytes "a") nil)))))
