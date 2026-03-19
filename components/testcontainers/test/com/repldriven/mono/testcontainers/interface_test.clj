(ns com.repldriven.mono.testcontainers.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system]]
    [clojure.test :refer [deftest is testing]]))

(deftest testcontainers-test
  (testing "Testcontainers should start and provide mapped ports"
    (with-test-system
     [sys "classpath:testcontainers/application-test.yml"]
     (is (= [8080 8081]
            (keys (system/instance sys [:helloworld :container-mapped-ports]))))
     (is (= (system/instance sys [:helloworld :container-mapped-exposed-port])
            (get (system/instance sys [:helloworld :container-mapped-ports])
                 8080))))))
