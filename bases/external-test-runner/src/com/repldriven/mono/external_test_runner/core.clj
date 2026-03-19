(ns com.repldriven.mono.external-test-runner.core
  (:require
    [org.corfield.external-test-runner.interface :as core]
    [polylith.clj.core.test-runner-contract.interface :as
     test-runner-contract]))

(set! *warn-on-reflection* true)

(defn create
  "Create an external test runner that wraps Corfield's implementation with setup/teardown support.
   Uses a custom main namespace that supports SKIP_META and FOCUS_META environment variables."
  [opts]
  (let [corfield-runner (core/create opts)]
    (reify
     test-runner-contract/TestRunner
       (test-runner-name [_]
         (test-runner-contract/test-runner-name corfield-runner))
       (test-sources-present? [_]
         (test-runner-contract/test-sources-present? corfield-runner))
       (tests-present? [_ ctx]
         (test-runner-contract/tests-present? corfield-runner ctx))
       (run-tests [_ {:keys [setup-fn teardown-fn is-verbose] :as ctx}]
         ;; External test runners must handle setup/teardown manually
         (when setup-fn
           (when is-verbose (println "Running setup function"))
           (setup-fn))
         (try (test-runner-contract/run-tests corfield-runner ctx)
              (finally (when teardown-fn
                         (when is-verbose
                           (println "Running teardown function"))
                         (teardown-fn)))))
     test-runner-contract/ExternalTestRunner
       (external-process-namespace [_]
         ;; Use our custom main namespace that supports
         ;; SKIP_META/FOCUS_META
         "com.repldriven.mono.external-test-runner.main"))))
