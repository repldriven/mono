(ns com.repldriven.mono.auth.interface-test
  (:require
    [com.repldriven.mono.auth.interface :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(def ^:private signer {:secret "test-secret-not-for-production"})

(deftest hash-password-test
  (testing "a hash verifies against its own plaintext and nothing else"
    (let [hashed (SUT/hash-password "correct horse")]
      (is (string? hashed))
      (is (true? (SUT/verify-password "correct horse" hashed)))
      (is (false? (SUT/verify-password "wrong horse" hashed)))))
  (testing "hashing is salted, so the same password hashes differently"
    (let [a (SUT/hash-password "same")
          b (SUT/hash-password "same")]
      (is (not= a b))
      (is (true? (SUT/verify-password "same" a)))
      (is (true? (SUT/verify-password "same" b)))))
  (testing "an empty or missing password is rejected rather than hashed"
    (is (error/rejection? (SUT/hash-password "")))
    (is (error/rejection? (SUT/hash-password nil))))
  (testing "a stored hash that cannot be parsed is not a match"
    ;; Corrupt or foreign hashes are an ordinary condition; nothing that is
    ;; not a match should read as one.
    (is (false? (SUT/verify-password "anything" "not-a-hash")))
    (is (false? (SUT/verify-password "anything" nil)))))

(deftest sign-token-test
  (testing "claims survive a signing round trip"
    (let [jwt (SUT/sign-token signer {:sub "user-1" :username "jake"})]
      (is (string? jwt))
      (let [claims (SUT/verify-token signer jwt)]
        (is (= "user-1" (:sub claims)))
        (is (= "jake" (:username claims)))
        (is (int? (:iat claims)))
        (is (int? (:exp claims))))))
  (testing "a token signed with another secret is not accepted"
    (let [jwt (SUT/sign-token {:secret "a-different-secret"} {:sub "user-1"})]
      (is (error/unauthorized? (SUT/verify-token signer jwt)))))
  (testing "an expired token is not accepted"
    (let [jwt (SUT/sign-token (assoc signer :ttl-seconds -60) {:sub "user-1"})]
      (is (error/unauthorized? (SUT/verify-token signer jwt)))))
  (testing "a malformed or absent token is unauthorized, not an error"
    (is (error/unauthorized? (SUT/verify-token signer "not.a.jwt")))
    (is (error/unauthorized? (SUT/verify-token signer "")))
    (is (error/unauthorized? (SUT/verify-token signer nil))))
  (testing "a signer with no secret is a fault, not an unauthorized caller"
    ;; The distinction matters: one is misconfiguration, the other is a
    ;; request. They want different alerts and different status codes.
    (is (error/error? (SUT/sign-token {} {:sub "user-1"})))
    (is (error/error? (SUT/verify-token {} "any.token.here")))))

(deftest unverified-claims-test
  (testing "the claims come back without the signature being checked"
    (let [jwt (SUT/sign-token {:secret "another-secret"}
                              {:sub "user-1" :iss "https://a"})]
      (is (= "https://a" (:iss (SUT/unverified-claims jwt))))
      (is (= "user-1" (:sub (SUT/unverified-claims jwt))))))
  (testing "anything that is not a JWT with a JSON payload is nil"
    (doseq [garbage [nil "" "not.a.jwt" "a.b" "a.!!!.c" (str "a." "e30" ".c")]]
      (is (or (nil? (SUT/unverified-claims garbage))
              (= {} (SUT/unverified-claims garbage)))
          (pr-str garbage)))))

(deftest header->token-test
  (testing "both Token and Bearer are accepted, case-insensitively"
    (is (= "abc" (SUT/header->token "Token abc" SUT/default-schemes)))
    (is (= "abc" (SUT/header->token "Bearer abc" SUT/default-schemes)))
    (is (= "abc" (SUT/header->token "token abc" SUT/default-schemes)))
    (is (= "abc" (SUT/header->token "  Token   abc  " SUT/default-schemes))))
  (testing "an unknown scheme, or no scheme at all, yields nothing"
    (is (nil? (SUT/header->token "Basic abc" SUT/default-schemes)))
    (is (nil? (SUT/header->token "abc" SUT/default-schemes)))
    (is (nil? (SUT/header->token "Token" SUT/default-schemes)))
    (is (nil? (SUT/header->token "Token " SUT/default-schemes)))
    (is (nil? (SUT/header->token nil SUT/default-schemes))))
  (testing "the accepted set is a parameter"
    (is (nil? (SUT/header->token "Bearer abc" #{"token"})))
    (is (= "abc" (SUT/header->token "Bearer abc" #{"bearer"})))))
