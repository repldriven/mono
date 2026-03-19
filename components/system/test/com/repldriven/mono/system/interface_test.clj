(ns com.repldriven.mono.system.interface-test
  (:require
    [com.repldriven.mono.system.interface :as SUT]
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

(deftest system-lifecycle
  (testing "A started empty system passes system?"
    (SUT/with-system [sys (SUT/start {:system/defs {}})]
      (is (SUT/system? sys)))))
