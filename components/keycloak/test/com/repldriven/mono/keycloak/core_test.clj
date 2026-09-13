(ns com.repldriven.mono.keycloak.core-test
  (:require
    [com.repldriven.mono.keycloak.core :as SUT]

    [com.repldriven.mono.utility.interface :as util]

    [buddy.core.keys :as buddy-keys]
    [buddy.sign.jwt :as jwt]

    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing]])
  (:import
    (java.security KeyPairGenerator)
    (java.util Base64)))

;; The brick reads the signing key from a PEM file, so the round-trip
;; test needs a real one on disk rather than an in-memory key.
(defn- write-key-pair!
  []
  (let [generator (doto (KeyPairGenerator/getInstance "RSA")
                    (.initialize 2048))
        key-pair (.generateKeyPair generator)
        encoder (Base64/getMimeEncoder 64 (.getBytes "\n" "UTF-8"))
        pem (str "-----BEGIN PRIVATE KEY-----\n"
                 (.encodeToString encoder (.getEncoded (.getPrivate key-pair)))
                 "\n-----END PRIVATE KEY-----\n")
        file (doto (java.io.File/createTempFile "keycloak-test-key" ".pem")
               (.deleteOnExit))]
    (spit file pem)
    {:path (.getAbsolutePath file) :public-key (.getPublic key-pair)}))

(deftest client-assertion-claims-test
  (let [now-ms 1750000000000
        claims (SUT/client-assertion-claims {:client-id "queenswood-admin"
                                             :audience "https://kc/token"
                                             :jti "a-jti"
                                             :now-ms now-ms})]
    (testing "the client authenticates as itself"
      (is (= "queenswood-admin" (:iss claims)))
      (is (= "queenswood-admin" (:sub claims))))
    (testing "the audience is claimed verbatim"
      (is (= "https://kc/token" (:aud claims))))
    (testing "claims are epoch seconds, not the millis they came from"
      (is (= (quot now-ms 1000) (:iat claims))))
    (testing "the assertion is short-lived"
      (is (= (quot SUT/client-assertion-ttl-ms 1000)
             (- (:exp claims) (:iat claims)))))
    (testing "a jti is carried so Keycloak can reject a replay"
      (is (= "a-jti" (:jti claims))))))

(deftest client-assertion-signing-test
  (testing "an assertion signed from a PEM file verifies against its key"
    (let [{:keys [path public-key]} (write-key-pair!)
          claims (SUT/client-assertion-claims {:client-id "queenswood-admin"
                                               :audience "https://kc/token"
                                               :jti "a-jti"
                                               :now-ms (util/now)})
          token (jwt/sign claims (buddy-keys/private-key path) {:alg :rs256})]
      (is (string? token))
      (is (= "queenswood-admin"
             (:iss (jwt/unsign token public-key {:alg :rs256})))))))

(deftest assertion-audience-test
  (let [internal {:base-url "http://keycloak.svc:8080" :realm "queenswood"}
        public (assoc internal
                      :expected-issuer
                      "https://sso.example/realms/queenswood")
        path "/realms/queenswood/protocol/openid-connect/token"]
    (testing "with no public issuer, the base-url endpoint"
      (is (= (str "http://keycloak.svc:8080" path)
             (SUT/assertion-audience internal))))
    ;; Keycloak validates the audience against its own frontend URL,
    ;; never against the address the request arrived on.
    (testing "a public issuer wins over base-url"
      (is (= (str "https://sso.example" path) (SUT/assertion-audience public))))
    (testing "the audience is independent of where the request is posted"
      (is (not= (SUT/assertion-audience internal)
                (SUT/assertion-audience public))))))

(deftest token-error-detail-test
  (testing "Keycloak's own words carry through, so a refusal says why"
    (is (= {:error "invalid_client" :error-description "Invalid token audience"}
           (SUT/token-error-detail {:error "invalid_client"
                                    :error_description
                                    "Invalid token audience"}))))
  (testing "a response with nothing to add yields nothing to merge"
    (is (= {} (SUT/token-error-detail {:access_token "t"})))
    (is (nil? (SUT/token-error-detail nil)))))

(deftest client-assertion-type-test
  (testing "the assertion type is the RFC 7523 URN Keycloak expects"
    (is (= "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
           SUT/client-assertion-type))))

(deftest missing-key-file-test
  (testing "a key file that is not there fails rather than throwing"
    (is (thrown? Exception
                 (buddy-keys/private-key (str (io/file "does-not-exist"
                                                       "key.pem")))))))
