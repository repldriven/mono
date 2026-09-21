(ns com.repldriven.mono.server.interceptors-test
  (:require
    [com.repldriven.mono.server.interface :as SUT]

    [com.repldriven.mono.auth.interface :as auth]
    [com.repldriven.mono.identity-provider.interface :as idp]

    [reitit.core :as r]
    [reitit.http :as http]

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

(deftest authenticate-with-many-providers-test
  (let [a (idp/local-provider {:issuer "https://a.test"})
        b (idp/local-provider {:issuer "https://b.test"})
        account (idp/create-service-account b
                                            {:bank-id "bank-b" :audience "api"})
        token (:access_token (idp/exchange-client-credentials b account))]
    (testing "the provider whose issuer the token names is the one asked"
      (let [ctx (enter SUT/authenticate-with-provider
                       {:identity-providers [a b] :credential token})]
        (is (= "bank-b" (get-in ctx [:request :auth-claims :sub])))))
    (testing "a token from an issuer no provider answers to sets nothing"
      (let [ctx (enter SUT/authenticate-with-provider
                       {:identity-providers [a] :credential token})]
        (is (nil? (get-in ctx [:request :auth-claims])))
        (is (nil? (:response ctx)))))
    (testing "the set decides even when one provider is on the request too"
      (let [ctx (enter SUT/authenticate-with-provider
                       {:identity-provider a
                        :identity-providers [a b]
                        :credential token})]
        (is (= "bank-b" (get-in ctx [:request :auth-claims :sub])))))
    (testing "expected audiences may be any collection"
      (let [ctx (enter SUT/authenticate-with-provider
                       {:identity-providers [a b]
                        :expected-audiences ["api"]
                        :credential token})]
        (is (= "bank-b" (get-in ctx [:request :auth-claims :sub])))))))

(deftest claims->scopes-test
  (testing "the scope claim and the realm roles become scopes"
    (let [ctx (enter SUT/claims->scopes
                     {:auth-claims {:scope "openid read:things"
                                    :realm_access {:roles ["admin"]}}})]
      (is (= #{"openid" "read:things" "admin"}
             (get-in ctx [:request :auth-scopes])))))
  (testing "scopes already granted are kept"
    (let [ctx (enter SUT/claims->scopes
                     {:auth-claims {:scope "openid"}
                      :auth-scopes #{"org:viewer"}})]
      (is (= #{"openid" "org:viewer"} (get-in ctx [:request :auth-scopes])))))
  (testing "no claims sets nothing"
    (is (nil? (get-in (enter SUT/claims->scopes {}) [:request :auth-scopes])))))

(deftest require-scopes-test
  (let [gate (fn [& objects] {:openapi {:security (vec objects)}})
        viewer (gate {"bearerAuth" ["viewer"]})
        either (gate {"bearerAuth" ["admin"]} {"bearerAuth" ["viewer"]})
        both (gate {"bearerAuth" ["admin" "viewer"]})
        claims {:sub "user-1"}
        granted (fn [& scopes] {:auth-claims claims :auth-scopes (set scopes)})]
    (testing "an operation with no security compiles to nothing"
      (is (nil? ((:compile SUT/require-scopes) {:openapi {}} nil)))
      (is (nil? ((:compile SUT/require-scopes) {:openapi {:security []}} nil))))
    (testing "no claims is a 401, in the route's own shape when it has one"
      (let [ctx (enter SUT/require-scopes viewer {})]
        (is (= 401 (get-in ctx [:response :status])))
        (is (= "server/unauthorized" (get-in ctx [:response :body :type]))))
      (let [mine {:status 401 :body {:errors {:token ["is missing"]}}}]
        (is (= mine
               (:response (enter SUT/require-scopes
                                 (assoc viewer :unauthorized mine)
                                 {}))))))
    (testing "scopes meeting the gate pass, and others are a 403"
      (is (nil? (:response
                 (enter SUT/require-scopes viewer (granted "viewer")))))
      (let [ctx (enter SUT/require-scopes viewer (granted "other"))]
        (is (= 403 (get-in ctx [:response :status])))
        (is (= "server/forbidden" (get-in ctx [:response :body :type]))))
      (let [mine {:status 403 :body {:errors {:role ["is insufficient"]}}}]
        (is (= mine
               (:response (enter SUT/require-scopes
                                 (assoc viewer :forbidden mine)
                                 (granted)))))))
    (testing
      "requirement objects are alternatives, and the scopes within
              one are all required"
      (is (nil? (:response
                 (enter SUT/require-scopes either (granted "viewer")))))
      (is (nil? (:response
                 (enter SUT/require-scopes either (granted "admin")))))
      (is (= 403
             (get-in (enter SUT/require-scopes both (granted "viewer"))
                     [:response :status])))
      (is (nil? (:response
                 (enter SUT/require-scopes both (granted "admin" "viewer"))))))
    (testing "a scheme with no scopes is met by any authenticated caller"
      (is (nil? (:response (enter SUT/require-scopes
                                  (gate {"bearerAuth" []})
                                  {:auth-claims claims})))))
    (testing
      "a gate outside the vocabulary is refused while the router is
              built"
      (is (thrown? clojure.lang.ExceptionInfo
                   ((:compile SUT/require-scopes)
                    (assoc viewer :scopes #{"admin"})
                    nil))))))

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

(defn- ok [_] {:status 200})

(defn- router
  "A router with `validate-security` on every route and `data` merged into
  each route's data, as an API declares its vocabulary at the root."
  [routes data]
  (http/router routes
               {:data (merge {:interceptors [SUT/validate-security]} data)}))

(defn- refusal
  "The ex-data of the refusal building the router throws, or nil when it
  builds."
  [routes data]
  (try (router routes data)
       nil
       (catch clojure.lang.ExceptionInfo e (ex-data e))))

(defn- chain-names
  [router path method]
  (->> (r/match-by-path router path)
       :result
       method
       :interceptors
       (map :name)))

(deftest validate-security-test
  (let [scopes #{"viewer" "developer" "admin"}
        gated (fn [& names] [["/things"
                              {:get {:openapi {:security [{"bearerAuth"
                                                           (vec names)}]}
                                     :summary "List things"
                                     :handler ok}}]])]
    (testing
      "a gate inside the vocabulary builds, and validation adds nothing
              to the chain a request runs"
      (let [router (router (gated "viewer") {:scopes scopes})]
        (is (= [:reitit.interceptor/handler]
               (chain-names router "/things" :get)))))
    (testing "an operation with no security, or an empty one, is public"
      (doseq [routes [[["/things" {:get {:handler ok}}]]
                      [["/things"
                        {:get {:openapi {:security []} :handler ok}}]]]]
        (is (nil? (refusal routes {:scopes scopes})) (pr-str routes))))
    (testing "a scheme with no scopes is refused once a vocabulary is declared"
      (is (= #{"bearerAuth"} (:no-scopes (refusal (gated) {:scopes scopes})))))
    (testing "without a vocabulary a scheme with no scopes is presence alone"
      (is (nil? (refusal (gated) {}))))
    (testing
      "a scope outside the vocabulary is refused, and the refusal
              names the operation"
      (let [refused (refusal (gated "viewer" "org") {:scopes scopes})]
        (is (= #{"org"} (:unknown-scopes refused)))
        (is (= "List things" (:operation refused)))))
    (testing "a method's gate stacked on its route's is refused"
      (let [routes [["/things"
                     {:openapi {:security [{"bearerAuth" ["admin"]}]}
                      :get {:openapi {:security [{"bearerAuth" ["viewer"]}]}
                            :handler ok}}]]
            refused (refusal routes
                             {:scopes scopes :exclusive-scopes [scopes]})]
        (is (= #{"admin" "viewer"} (:exclusive refused)))
        (is (= [{"bearerAuth" ["admin"]} {"bearerAuth" ["viewer"]}]
               (:security refused)))))
    (testing "a method marked ^:replace declares its own gate"
      (let [routes [["/things"
                     {:openapi {:security [{"bearerAuth" ["admin"]}]}
                      :get {:openapi {:security ^:replace
                                                [{"bearerAuth" ["viewer"]}]}
                            :handler ok}}]]]
        (is (nil? (refusal routes
                           {:scopes scopes :exclusive-scopes [scopes]})))))
    (testing "a gate that is not requirement objects is refused as malformed"
      (let [routes [["/things"
                     {:get {:openapi {:security [{"bearerAuth" "viewer"}]}
                            :handler ok}}]]]
        (is (contains? (refusal routes {}) :malformed))))))
