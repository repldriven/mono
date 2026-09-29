(ns com.repldriven.mono.system.interface-test
  (:require
    [com.repldriven.mono.system.interface :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(deftest system-predicate
  (testing "system? returns false for non-system values"
    (is (not (SUT/system? nil)))
    (is (not (SUT/system? {})))
    (is (not (SUT/system? "string")))))

(deftest defs-transforms-config
  (testing "defs extracts :system key and wraps in :system/defs"
    (is (= {:system/defs {:group {:comp {:some "config"}}}}
           (SUT/defs {:system {:group {:comp {:some "config"}}}}))))
  (testing "defs accepts a custom path"
    (is (= {:system/defs {:group {:comp {}}}}
           (SUT/defs {:custom {:group {:comp {}}}} [:custom])))))

(deftest defs-refuses-a-kind-nobody-registered
  (testing "a kind no defcomponents registered is an anomaly, not a component"
    (let [result (SUT/defs {:system {:group {:comp {:system/component-kind
                                                    "nobody/registered"
                                                    :some "config"}}}})]
      (is (error/anomaly? result))
      (is (= :system/unknown-component-kind (error/kind result)))
      (is (= :comp (:component (error/payload result))))))
  (testing "a value with no kind is configuration, and passes through"
    (is (= {:system/defs {:group {:comp {:some "config"}}}}
           (SUT/defs {:system {:group {:comp {:some "config"}}}})))))

(deftest system-lifecycle
  (testing "A started empty system passes system?"
    (SUT/with-system [sys (SUT/start {:system/defs {}})]
      (is (SUT/system? sys)))))

(deftest stop-on-shutdown-stops-the-system
  (testing "the registered hook stops the system when the JVM shuts down"
    (let [stopped (promise)
          sys (SUT/start {:system/defs
                          {:group {:comp {:system/start (fn [_] :started)
                                          :system/stop
                                          (fn [_] (deliver stopped true))}}}})
          hook (SUT/stop-on-shutdown sys)]
      (try (is (instance? Thread hook))
           (.run ^Thread hook)
           (is (true? (deref stopped 1000 false)))
           (finally (.removeShutdownHook (Runtime/getRuntime) hook))))))
