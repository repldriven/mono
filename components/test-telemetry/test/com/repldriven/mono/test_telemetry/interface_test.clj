(ns ^:eftest/synchronized com.repldriven.mono.test-telemetry.interface-test
  (:require
    [com.repldriven.mono.test-telemetry.interface :as SUT]

    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.test-system.interface :refer [with-test-system]]

    [clojure.test :refer [deftest is testing]]))

(defn- span-names
  [instance]
  (into #{} (map #(.getName %)) (SUT/finished-spans instance)))

(deftest otel-sdk-component-test
  (testing "a closed span is readable at once, with no flush to wait out"
    (with-test-system [sys "classpath:test-telemetry/application-test.yml"]
                      (let [otel (system/instance sys [:telemetry :otel-sdk])]
                        (is (some? (:sdk otel)))
                        (SUT/clear-spans! otel)
                        ;; Through the instance's own tracer: the default
                        ;; tracer is process-wide, and another namespace's
                        ;; telemetry component may have taken it since
                        ;; start.
                        (telemetry/with-span {:name "in-memory-work"
                                              :tracer (SUT/tracer otel)}
                                             :done)
                        (is (contains? (span-names otel) "in-memory-work")))))
  (testing "and the collected spans do not outlive the component"
    (let [stopped (atom nil)]
      (with-test-system [sys "classpath:test-telemetry/application-test.yml"]
                        (let [otel (system/instance sys [:telemetry :otel-sdk])]
                          (reset! stopped otel)
                          (telemetry/with-span {:name "before-stop"
                                                :tracer (SUT/tracer otel)}
                                               :done)
                          (is (seq (SUT/finished-spans otel)))))
      (is (empty? (SUT/finished-spans @stopped))))))

(deftest span-accessors-ignore-other-instances-test
  (testing "an OTLP instance collects nothing in memory"
    (is (nil? (SUT/finished-spans {:sdk :some-sdk :exporter :otlp-exporter})))
    (is (nil? (SUT/clear-spans! {:sdk :some-sdk :exporter :otlp-exporter}))))
  (testing "and a telemetry component that never started is nil, not a map"
    (is (nil? (SUT/finished-spans nil)))
    (is (nil? (SUT/clear-spans! nil)))))

(deftest with-span-tests-test
  (testing "the macro asserts the named spans share one trace"
    (SUT/with-span-tests [_ ["outer" "inner"]]
                         (telemetry/with-span ["outer" {}]
                                              (telemetry/with-span ["inner" {}]
                                                                   :done)))))

(deftest with-span-tests-awaits-another-thread-test
  (testing "a span still open when the caller resumes is waited for"
    ;; The shape `command/process` has: the reply is sent from INSIDE the
    ;; process-command span, so the caller unblocks while the consumer
    ;; thread is still unwinding it. Reading finished spans once loses that
    ;; race on a loaded machine.
    (SUT/with-span-tests [_ ["async-work"]]
                         (let [traceparent (telemetry/inject-traceparent)
                               unblocked (promise)]
                           (future (telemetry/with-span-parent
                                    "async-work"
                                    (telemetry/extract-parent-context
                                     {:traceparent traceparent :tracestate nil})
                                    {}
                                    (fn []
                                      (deliver unblocked :replied)
                                      ;; the span closes well after the
                                      ;; caller has moved on
                                      (Thread/sleep 200))))
                           (is (= :replied
                                  (deref unblocked 5000 :timed-out)))))))
