(ns com.repldriven.mono.server.interceptors-test
  (:require
    [com.repldriven.mono.server.interface :as SUT]

    [com.repldriven.mono.auth.interface :as auth]
    [com.repldriven.mono.identity-provider.interface :as idp]

    [clojure.test :refer [deftest is testing]]))

(def ^:private signer {:secret "test-secret-not-for-production"})

(defn- enter
  ([interceptor request] (enter interceptor nil request))
  ([interceptor data request]
   (let [{:keys [compile]} interceptor
         interceptor (if compile (compile data nil) interceptor)]
     ((:enter interceptor) {:request request}))))

(deftest credential-test
  (testing "the credential lands on the request with its scheme stripped"
    (doseq [header ["Token abc" "Bearer abc" "token abc"]]
      (let [ctx (enter SUT/credential {:headers {"authorization" header}})]
        (is (= "abc" (get-in ctx [:request :credential])) header))))
  (testing "no header and another scheme set nothing and refuse nothing"
    (doseq [headers [{} {"authorization" "Basic abc"}]]
      (let [ctx (enter SUT/credential {:headers headers})]
        (is (nil? (get-in ctx [:request :credential])) (pr-str headers))
        (is (nil? (:response ctx)))))))

(deftest authenticate-with-signer-test
  (let [jwt (auth/sign-token signer {:sub "user-1"})]
    (testing "a valid credential lands as claims on the request"
      (let [ctx (enter SUT/authenticate-with-signer
                       {:signer signer :credential jwt})]
        (is (= "user-1" (get-in ctx [:request :auth-claims :sub])))))
    (testing "no credential, a bad token and no signer all set nothing"
      ;; Not an error: endpoints with optional authentication depend on
      ;; this passing through untouched.
      (doseq [request [{:signer signer} {:signer signer :credential "not.a.jwt"}
                       {:credential jwt}]]
        (let [ctx (enter SUT/authenticate-with-signer request)]
          (is (nil? (get-in ctx [:request :auth-claims])) (pr-str request))
          (is (nil? (:response ctx))))))))

(deftest authenticate-with-provider-test
  (let [provider (idp/local-provider {})
        account (idp/create-service-account provider
                                            {:bank-id "bank-1" :audience "api"})
        issued (idp/exchange-client-credentials provider account)
        token (:access_token issued)]
    (testing "a token the provider issued lands as claims on the request"
      (let [ctx (enter SUT/authenticate-with-provider
                       {:identity-provider provider
                        :expected-audiences #{"api"}
                        :credential token})]
        (is (= "bank-1" (get-in ctx [:request :auth-claims :sub])))))
    (testing "without expected audiences any audience is accepted"
      (let [ctx (enter SUT/authenticate-with-provider
                       {:identity-provider provider :credential token})]
        (is (= "bank-1" (get-in ctx [:request :auth-claims :sub])))))
    (testing "an unexpected audience, a bad token and no provider set nothing"
      (doseq [request [{:identity-provider provider
                        :expected-audiences #{"other"}
                        :credential token}
                       {:identity-provider provider :credential "not.a.jwt"}
                       {:credential token}]]
        (let [ctx (enter SUT/authenticate-with-provider request)]
          (is (nil? (get-in ctx [:request :auth-claims])) (pr-str request))
          (is (nil? (:response ctx))))))))

(deftest require-auth-test
  (testing "a request without claims is terminated with a 401"
    (let [ctx (enter SUT/require-auth {:headers {}})]
      (is (= 401 (get-in ctx [:response :status])))
      (is (= "server/unauthorized" (get-in ctx [:response :body :type])))))
  (testing "a request with claims passes through untouched"
    (let [ctx (enter SUT/require-auth {:auth-claims {:sub "user-1"}})]
      (is (nil? (:response ctx)))))
  (testing
    "the route's :unauthorized is the response, so an API keeps its shape"
    (let [realworld {:status 401 :body {:errors {:token ["is missing"]}}}
          ctx (enter SUT/require-auth {:unauthorized realworld} {:headers {}})]
      (is (= realworld (:response ctx))))))
