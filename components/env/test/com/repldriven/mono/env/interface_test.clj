(ns com.repldriven.mono.env.interface-test
  (:require
    [com.repldriven.mono.env.interface :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :as test :refer [deftest is testing]])
  (:import
    (java.util UUID)))

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

(deftest long-tag-test
  (let [config (SUT/config "classpath:env/long-test.yml" :default)]
    (testing "!long coerces, including a quoted scalar YAML reads as a string"
      (is (= 8091 (get-in config [:system :from-literal])))
      (is (= 8091 (get-in config [:system :from-quoted])))
      (is (instance? Long (get-in config [:system :from-quoted]))))
    (testing "!long wraps !or, so an env var can have a default and a type"
      ;; The coercion has to be outside the or: aero throws parsing "" if
      ;; it is inside, before the fallback is reached.
      (is (= 8080 (get-in config [:system :from-env])))
      (is (instance? Long (get-in config [:system :from-env]))))
    (testing "!or alone falls back without coercing"
      (is (= "fallback" (get-in config [:system :or-alone]))))))

(deftest keyword-tag-test
  (let [config (SUT/config "classpath:env/keyword-test.yml" :default)]
    (testing "!keyword reads a scalar, and a one-item sequence"
      (is (= :starttls (get-in config [:system :from-literal])))
      (is (= :none (get-in config [:system :from-sequence]))))
    (testing "!keyword wraps !or, so an env var can have a default and a type"
      (is (= :tls (get-in config [:system :from-env]))))
    (testing "!or alone falls back without coercing"
      (is (= "tls" (get-in config [:system :or-alone]))))))

(def ^:private parsed-uuid #uuid "0192f4e2-8f7a-7c3d-9b1e-2a4c6e8f0a1b")

(deftest uuid-tag-test
  (let [config (SUT/config "classpath:env/uuid-test.yml" :default)]
    (testing "!random-uuid generates a v7"
      (let [generated (get-in config [:system :generated])]
        (is (instance? UUID generated))
        (is (= 7 (.version generated)))))
    (testing "and !uuid parses a literal"
      (is (= parsed-uuid (get-in config [:system :parsed]))))
    (testing "a generated value is new on every read, not memoised"
      (is (not= (get-in (SUT/config "classpath:env/uuid-test.yml" :default)
                        [:system :generated])
                (get-in (SUT/config "classpath:env/uuid-test.yml" :default)
                        [:system :generated])))))
  (testing "the same tags work in EDN config"
    (let [config (SUT/config "classpath:env/uuid-test.edn" :default)]
      (is (instance? UUID (get-in config [:system :generated])))
      (is (= parsed-uuid (get-in config [:system :parsed])))
      (is (re-matches #"meta-[0-9a-f-]{36}"
                      (get-in config [:system :joined])))))
  (testing "a malformed literal is an anomaly, not a throw"
    (is (error/anomaly? (SUT/config "classpath:env/bad-uuid-test.yml" :dev)))))

(deftest join-tag-test
  (let [config (SUT/config "classpath:env/uuid-test.yml" :default)]
    (testing "!join concatenates its sequence into one string"
      (is (= "meta-fixed" (get-in config [:system :plain-join]))))
    (testing "and takes a generated !random-uuid, block or flow"
      ;; YAML rejects a bare tag as the last entry of a flow sequence, so
      ;; the flow form has to spell out the empty scalar.
      (is (re-matches #"meta-[0-9a-f-]{36}"
                      (get-in config [:system :block-join])))
      (is (re-matches #"meta-[0-9a-f-]{36}"
                      (get-in config [:system :flow-join]))))))

(deftest profile-selects-one-branch-test
  (testing "the selected branch is joined, the other is a plain value"
    (is (= "meta"
           (get-in (SUT/config "classpath:env/uuid-test.yml" :default)
                   [:system :path])))
    (is (re-matches #"meta-[0-9a-f-]{36}"
                    (get-in (SUT/config "classpath:env/uuid-test.yml" :dev)
                            [:system :path]))))
  (testing "and an unselected branch is never evaluated"
    ;; The dev branch holds a #uuid that cannot parse. Reading :default
    ;; must not touch it — which also means a broken value in an unused
    ;; branch stays silent until that profile is selected.
    (is (= "meta"
           (get-in (SUT/config "classpath:env/bad-uuid-test.yml" :default)
                   [:system :path])))))
