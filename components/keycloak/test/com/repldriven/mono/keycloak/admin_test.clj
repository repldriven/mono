(ns com.repldriven.mono.keycloak.admin-test
  (:require
    [com.repldriven.mono.keycloak.interface]
    [com.repldriven.mono.testcontainers.interface]

    [com.repldriven.mono.keycloak.protocol :as protocol]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.identity-provider.interface :as SUT]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as util]

    [clojure.test :refer [deftest is testing]]))

(def ^:private test-config "classpath:keycloak/application-test.yml")

(defn- stale!
  "Cache a token Keycloak did not sign, as one minted before its keys
  changed is by the time it is next used."
  [client]
  (reset! (protocol/-admin-token-atom client)
    {:access-token "stale" :expires-in 3600 :fetched-at (util/now)}))

(deftest admin-test
  (with-test-system
   [sys test-config]
   (let [client (system/instance sys [:keycloak :identity-provider])
         viewer (system/instance sys [:keycloak :viewer])]
     (testing "a service account is created, rotated, re-pointed and revoked"
       (nom-test> [created (SUT/create-service-account client
                                                       {:bank-id "bnk.a"
                                                        :name "A"})
                   _ (is (= "bnk.a" (:client-id created)))
                   _ (is (string? (:client-secret created)))
                   rotated (SUT/rotate-secret client "bnk.a")
                   _ (is (not= (:client-secret created)
                               (:client-secret rotated)))
                   _ (SUT/update-service-account-audience client "bnk.a" "aud")
                   _ (SUT/revoke-service-account client "bnk.a")])
       (is (= :keycloak/client-not-found
              (error/kind (SUT/rotate-secret client "bnk.a")))))
     (testing "creating a client already there answers as the first did"
       (nom-test> [_ (SUT/create-service-account client {:bank-id "bnk.b"})
                   again (SUT/create-service-account client {:bank-id "bnk.b"})
                   _ (is (= "bnk.b" (:client-id again)))]))
     (testing "a cached token Keycloak refuses is replaced, and the call made"
       (stale! client)
       (nom-test> [created (SUT/create-service-account client
                                                       {:bank-id "bnk.c"})
                   _ (is (= "bnk.c" (:client-id created)))
                   _ (is (not= "stale"
                               (:access-token @(protocol/-admin-token-atom
                                                client))))]))
     (testing "a refusal a fresh token does not cure is an anomaly"
       (let [refused (SUT/create-service-account viewer {:bank-id "bnk.d"})]
         (is (= :keycloak/admin-request (error/kind refused)))
         (is (= 403 (:status (error/payload refused)))))))))
