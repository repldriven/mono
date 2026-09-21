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

(defn- enter
  [interceptor request]
  ((:enter interceptor) {:request request}))

(deftest credential-interceptor-test
  (let [interceptor (SUT/credential-interceptor)]
    (testing "the credential lands on the request with its scheme stripped"
      (let [ctx (enter interceptor {:headers {"authorization" "Bearer abc"}})]
        (is (= "abc" (get-in ctx [:request :credential])))))
    (testing "no header and a bad scheme set nothing and reject nothing"
      (doseq [headers [{} {"authorization" "Basic abc"}]]
        (let [ctx (enter interceptor {:headers headers})]
          (is (nil? (get-in ctx [:request :credential]))
              (str "should not set a credential for " (pr-str headers)))
          (is (nil? (:response ctx))))))
    (testing "the schemes and the key are parameters"
      (let [interceptor (SUT/credential-interceptor {:schemes #{"bearer"}
                                                     :credential-key :session})
            bearer (enter interceptor {:headers {"authorization" "Bearer abc"}})
            token (enter interceptor {:headers {"authorization" "Token abc"}})]
        (is (= "abc" (get-in bearer [:request :session])))
        (is (nil? (get-in token [:request :session])))))))

(deftest token-interceptor-test
  (let [jwt (SUT/sign-token signer {:sub "user-1"})
        interceptor (SUT/token-interceptor signer)]
    (testing "a valid credential lands as claims on the request"
      (let [ctx (enter interceptor
                       {:headers {"authorization" (str "Token " jwt)}})]
        (is (= "user-1" (get-in ctx [:request :auth-claims :sub])))))
    (testing "no header, a bad scheme and an invalid token all set nothing"
      ;; Not an error: endpoints with optional authentication depend on
      ;; this passing through untouched.
      (doseq [headers [{} {"authorization" "Basic abc"}
                       {"authorization" "Token not.a.jwt"}]]
        (let [ctx (enter interceptor {:headers headers})]
          (is (nil? (get-in ctx [:request :auth-claims]))
              (str "should not set claims for " (pr-str headers)))
          (is (nil? (:response ctx))))))
    (testing "the claims key is a parameter"
      (let [interceptor (SUT/token-interceptor signer {:claims-key :whoami})
            ctx (enter interceptor
                       {:headers {"authorization" (str "Token " jwt)}})]
        (is (= "user-1" (get-in ctx [:request :whoami :sub])))))))

(deftest require-auth-test
  (testing "a request without claims is terminated with the response"
    (let [ctx (enter (SUT/require-auth) {:headers {}})]
      (is (= 401 (get-in ctx [:response :status])))))
  (testing "a request with claims passes through untouched"
    (let [ctx (enter (SUT/require-auth) {:auth-claims {:sub "user-1"}})]
      (is (nil? (:response ctx)))))
  (testing "the response is a parameter, so an API keeps its own error shape"
    (let [realworld {:status 401 :body {:errors {:token ["is missing"]}}}
          ctx (enter (SUT/require-auth realworld) {:headers {}})]
      (is (= realworld (:response ctx))))))

(deftest signer-from-request-test
  (testing "a signer can be resolved from the request rather than fixed"
    ;; Interceptors are built at routing time, before a started system
    ;; component can reach them, so a keyword resolver is how a route gets
    ;; wired without threading the component through routing.
    (let [jwt (SUT/sign-token signer {:sub "user-1"})
          interceptor (SUT/token-interceptor :signer)
          ctx (enter interceptor
                     {:signer signer
                      :headers {"authorization" (str "Token " jwt)}})]
      (is (= "user-1" (get-in ctx [:request :auth-claims :sub])))))
  (testing "a plain signer map still works"
    (let [jwt (SUT/sign-token signer {:sub "user-2"})
          ctx (enter (SUT/token-interceptor signer)
                     {:headers {"authorization" (str "Token " jwt)}})]
      (is (= "user-2" (get-in ctx [:request :auth-claims :sub]))))))
