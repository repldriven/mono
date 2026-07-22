(ns com.repldriven.mono.secret.interface-test
  (:require
    [com.repldriven.mono.secret.interface :as SUT]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer [with-test-system]]
    [clojure.test :refer [deftest is testing]]))

(deftest secret-provider-test
  (testing "the wired secret provider resolves secrets by id"
    (with-test-system
     [sys "classpath:secret/application-test.yml"]
     (let [provider (system/instance sys [:secret :provider])
           env-provider (system/instance sys [:secret :env-provider])]
       (testing "a known secret id returns the secret bytes"
         (is (= "test-clearbank-api-key"
                (String. ^bytes (SUT/get-secret provider :clearbank/api-key)
                         "UTF-8"))))
       (testing "an unknown secret id returns an anomaly"
         (is (error/anomaly? (SUT/get-secret provider :unknown/secret))))
       (testing "the env provider reads a set environment variable"
         (is (pos? (alength ^bytes (SUT/get-secret env-provider :test/path)))))
       (testing "the env provider returns an anomaly for an unset variable"
         (is (error/anomaly? (SUT/get-secret env-provider :test/unset))))))))
