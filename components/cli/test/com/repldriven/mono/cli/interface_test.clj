(ns com.repldriven.mono.cli.interface-test
  (:require
    [com.repldriven.mono.cli.interface :as SUT]
    [clojure.test :as test :refer [deftest is testing]]))

(deftest validate-args-test
  (testing "Valid arguments with both config and profile"
    (let [result (SUT/validate-args "test-program"
                                    ["-c" "test.edn" "-p" "dev"])]
      (is (= {:options {:config-file "test.edn" :profile "dev"}} result))))
  (testing "Valid arguments with defaults"
    (let [result (SUT/validate-args "test-program" [])]
      (is (= {:options {:config-file "env.edn" :profile "default"}} result))))
  (testing "Help flag returns usage message"
    (let [result (SUT/validate-args "test-program" ["-h"])]
      (is (:ok? result))
      (is (string? (:exit-message result)))
      (is (re-find #"Usage:" (:exit-message result)))))
  (testing "Help flag with long form"
    (let [result (SUT/validate-args "test-program" ["--help"])]
      (is (:ok? result))
      (is (string? (:exit-message result)))))
  (testing "Invalid profile returns error"
    (let [result (SUT/validate-args "test-program" ["-p" "invalid"])]
      (is (nil? (:ok? result)))
      (is (string? (:exit-message result)))
      (is (re-find #"Bad profile name" (:exit-message result)))))
  (testing "Long form arguments work"
    (let [result (SUT/validate-args "test-program"
                                    ["--config-file" "app.edn" "--profile"
                                     "test"])]
      (is (= {:options {:config-file "app.edn" :profile "test"}} result))))
  (testing "Profile validation accepts valid profiles"
    (doseq [profile ["default" "dev" "test"]]
      (let [result (SUT/validate-args "test-program" ["-p" profile])]
        (is (= profile (get-in result [:options :profile]))
            (str "Profile " profile " should be accepted")))))
  (testing "Unknown option returns error"
    (let [result (SUT/validate-args "test-program" ["--unknown"])]
      (is (nil? (:ok? result)))
      (is (string? (:exit-message result)))
      (is (re-find #"Unknown option" (:exit-message result))))))
