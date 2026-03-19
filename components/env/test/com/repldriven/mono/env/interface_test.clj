(ns com.repldriven.mono.env.interface-test
  (:require
    [com.repldriven.mono.env.interface :as SUT]
    [clojure.test :as test :refer [deftest is testing]]))

(deftest edn-test
  (testing
    "A non-zero port number in config is preserved, ie `:port #port 80` -> `:port 80`"
    (let [environment (SUT/config "classpath:env/test-env.edn" :default)
          port (get-in environment [:system :port])]
      (is (= 80 port))))
  (testing
    "A zero port number in config returns an available local port,
            eg `:port #port 0` -> `:port 62457`"
    (let [environment (SUT/config "classpath:env/test-env.edn" :test)
          port (get-in environment [:system :port])]
      (is (and (>= port 1024) (<= port 65535))))))

(deftest yaml-test
  (testing
    "A non-zero port number in config is preserved, ie `:port #port 80` -> `:port 80`"
    (let [environment (SUT/config "classpath:env/application-test.yml" :default)
          port (get-in environment [:system :port])]
      (is (= 80 port))))
  (testing
    "A zero port number in config returns an available local port,
            eg `:port #port 0` -> `:port 62457`"
    (let [environment (SUT/config "classpath:env/application-test.yml" :test)
          port (get-in environment [:system :port])]
      (is (and (>= port 1024) (<= port 65535))))))
