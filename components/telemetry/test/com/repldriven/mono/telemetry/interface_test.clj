(ns com.repldriven.mono.telemetry.interface-test
  (:require
    [com.repldriven.mono.telemetry.interface :as SUT]

    [clojure.test :refer [deftest is testing]]))

(deftest with-span-runs-its-body-once-test
  (testing "a successful body runs once and its value is returned"
    (let [calls (atom 0)]
      (is (= :ok (SUT/with-span ["ok" {}] (swap! calls inc) :ok)))
      (is (= 1 @calls))))
  (testing "a throwing body runs once, and the exception is the caller's"
    ;; The fallback used to re-run the body, so a span around a payment
    ;; charged twice and swallowed the first failure.
    (let [calls (atom 0)]
      (is (thrown? clojure.lang.ExceptionInfo
                   (SUT/with-span ["boom" {}]
                                  (swap! calls inc)
                                  ;; nosemgrep: no-raw-throw
                                  (throw (ex-info "body failed" {})))))
      (is (= 1 @calls) "body must not be retried")))
  (testing "a body returning nil is not confused with a failure"
    (let [calls (atom 0)]
      (is (nil? (SUT/with-span ["nil" {}] (swap! calls inc) nil)))
      (is (= 1 @calls)))))

(deftest with-span-parent-runs-f-once-test
  (testing "f runs once whether it succeeds or throws"
    (let [calls (atom 0)]
      (is (= :ok
             (SUT/with-span-parent "ok" nil {} (fn [] (swap! calls inc) :ok))))
      (is (= 1 @calls)))
    (let [calls (atom 0)]
      (is (thrown? clojure.lang.ExceptionInfo
                   (SUT/with-span-parent "boom"
                                         nil
                                         {}
                                         (fn []
                                           (swap! calls inc)
                                           ;; nosemgrep: no-raw-throw
                                           (throw (ex-info "f failed" {}))))))
      (is (= 1 @calls) "f must not be retried"))))

(deftest counters-degrade-test
  (testing "counter functions are no-ops rather than throws"
    (let [c (SUT/counter {:name "test.counter"})]
      (is (nil? (SUT/inc-counter! c {:reason :test})))
      (is (nil? (SUT/add-counter! c 5 {:reason :test})))))
  (testing "and tolerate a counter that was never created"
    (is (nil? (SUT/inc-counter! nil {})))
    (is (nil? (SUT/add-counter! nil 5 {})))))

(deftest degrades-without-otel-test
  (testing "span data functions are harmless outside a span"
    ;; clj-otel hands back a no-op PropagatedSpan rather than nil here, so
    ;; the contract is "does not throw", not "returns nil".
    (is (some? (SUT/add-event "event" {})))
    (is (some? (SUT/set-attribute :k "v"))))
  (testing "extract-parent-context always yields a context"
    (is (some? (SUT/extract-parent-context {})))))
