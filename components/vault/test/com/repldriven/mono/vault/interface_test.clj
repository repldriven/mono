(ns com.repldriven.mono.vault.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system]]
    [com.repldriven.mono.utility.interface :as utility]
    [com.repldriven.mono.vault.interface :as SUT]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]))

(deftest vault-component-test
  (testing "Vault component should authenticate and read secrets"
    (with-test-system [sys "classpath:vault/application-test.yml"]
                      (let [client (system/instance sys [:vault :client])
                            vault-config (system/config sys :vault :container)
                            token (:vault-token vault-config)
                            secret (:secret-in-vault vault-config)]
                        (is (some? client))
                        (is (some?
                             (SUT/authenticate-client! client :token token)))
                        (let [[mount path] (-> secret
                                               first
                                               (str/split #"/"))
                              secret-props (-> secret
                                               rest)]
                          (is (= (SUT/read-secret client mount path)
                                 (utility/prop-seq->kw-map secret-props))))))))
