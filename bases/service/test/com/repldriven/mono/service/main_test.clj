(ns ^:eftest/synchronized com.repldriven.mono.service.main-test
  (:require
    com.repldriven.mono.test-schema.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.service.main :as SUT]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]
    [clojure.test :refer [deftest is testing]]))

(deftest main-test
  (testing "System should start and process commands"
    (let [sys (SUT/start "classpath:service/application-test.yml" :test)]
      (is (not (error/anomaly? sys)) "System should start")
      (is (system/system? sys) "System should be valid")
      (when (system/system? sys)
        (is (not (error/anomaly? (system/stop sys))))))))
