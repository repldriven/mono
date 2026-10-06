(ns com.repldriven.mono.keycloak.identity-provider-test
  (:require
    [com.repldriven.mono.keycloak.identity-provider :as SUT]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.identity-provider.interface :as identity-provider]
    [com.repldriven.mono.utility.interface :as util]

    [buddy.sign.jwt :as jwt]

    [clojure.test :refer [deftest is testing]])
  (:import
    (java.security KeyPair KeyPairGenerator)
    (java.security.interfaces RSAPublicKey)
    (java.util Base64)))

(def ^:private issuer "https://id.example/realms/test")

(defn- b64url
  [^bytes bs]
  (.encodeToString (.withoutPadding (Base64/getUrlEncoder)) bs))

(defn- key-pair
  ^KeyPair []
  (.generateKeyPair (doto (KeyPairGenerator/getInstance "RSA")
                      (.initialize 2048))))

(defn- jwks
  [^KeyPair kp kid]
  (let [^RSAPublicKey pub (.getPublic kp)]
    {:keys [{:kty "RSA"
             :kid kid
             :alg "RS256"
             :use "sig"
             :n (b64url (.toByteArray (.getModulus pub)))
             :e (b64url (.toByteArray (.getPublicExponent pub)))}]
     :fetched-at (util/now)}))

(defn- client
  [kp config]
  (let [c (SUT/->client (merge {:base-url "http://127.0.0.1:1"
                                :realm "test"
                                :admin-client-id "admin"
                                :admin-client-secret "secret"
                                :expected-issuer issuer}
                               config))]
    (reset! (:jwks c) (jwks kp "k1"))
    c))

(defn- token
  [^KeyPair kp]
  (let [now (quot (util/now) 1000)]
    (jwt/sign {:iss issuer :aud "api" :sub "s1" :iat now :exp (+ now 300)}
              (.getPrivate kp)
              {:alg :rs256 :header {:kid "k1"}})))

(deftest verified-token-is-kept-test
  (let [kp (key-pair)
        c (client kp {})
        t (token kp)
        opts {:expected-audiences #{"api"}}]
    (testing "a verified token is verified once"
      (is (= "s1" (:sub (identity-provider/verify-token c t opts))))
      (reset! (:jwks c) {:keys [] :fetched-at (util/now)})
      (is (= "s1" (:sub (identity-provider/verify-token c t opts)))))
    (testing "the audience is checked on every call"
      (is
       (error/anomaly?
        (identity-provider/verify-token c t {:expected-audiences #{"other"}}))))
    (testing "a token that failed is not kept"
      (let [other (token (key-pair))]
        (is (error/anomaly? (identity-provider/verify-token c other opts)))))))

(deftest verified-token-ttl-zero-test
  (testing "a zero ttl verifies every token afresh"
    (let [kp (key-pair)
          c (client kp {:verified-token-ttl-ms 0})
          t (token kp)
          opts {:expected-audiences #{"api"}}]
      (is (= "s1" (:sub (identity-provider/verify-token c t opts))))
      (reset! (:jwks c) {:keys [] :fetched-at (util/now)})
      (is (error/anomaly? (identity-provider/verify-token c t opts))))))
