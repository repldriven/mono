(ns com.repldriven.mono.testcontainers.interface-test
  (:require
    [com.repldriven.mono.testcontainers.interface :as SUT]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system]]
    [clojure.test :refer [deftest is testing]]))

(deftest reuse?-test
  (testing "a literal, or the string an env var carries, in any case"
    (is (true? (SUT/reuse? {:reuse true})))
    (is (true? (SUT/reuse? {:reuse "TRUE"})))
    (is (true? (SUT/reuse? {:reuse "1"})))
    (is (false? (SUT/reuse? {:reuse false})))
    (is (false? (SUT/reuse? {:reuse "false"})))
    (is (false? (SUT/reuse? {:reuse nil})))
    (is (false? (SUT/reuse? {})))))

(deftest testcontainers-test
  (testing "Testcontainers should start and provide mapped ports"
    (with-test-system
     [sys "classpath:testcontainers/application-test.yml"]
     (is (= [8080 8081]
            (keys (system/instance sys [:helloworld :container-mapped-ports]))))
     (is (= (system/instance sys [:helloworld :container-mapped-exposed-port])
            (get (system/instance sys [:helloworld :container-mapped-ports])
                 8080))))))
