(ns com.repldriven.mono.test-system.core-test
  (:require
    [com.repldriven.mono.test-system.core :as core]

    [clojure.test :refer [deftest is testing]])
  (:import
    (java.util.concurrent CountDownLatch Semaphore TimeUnit)))

(defn- run-threads
  "Start `n` threads running `f` and wait for them all."
  [n f]
  (let [threads (mapv (fn [_] (Thread. ^Runnable f)) (range n))]
    (run! #(.start ^Thread %) threads)
    (run! #(.join ^Thread %) threads)))

(defn- peak-overlap
  "How many of `n` bodies ran at once under `semaphore`, each holding
  its place for a moment."
  [semaphore n]
  (let [active (atom 0)
        peak (atom 0)]
    (run-threads n
                 #(core/with-permit semaphore
                                    (fn []
                                      (swap! peak max (swap! active inc))
                                      (Thread/sleep 50)
                                      (swap! active dec))))
    @peak))

(deftest with-permit-test
  (testing "a semaphore bounds how many bodies run at once"
    (is (= 1 (peak-overlap (Semaphore. 1 true) 4)))
    (is (<= (peak-overlap (Semaphore. 2 true) 4) 2)))
  (testing "no semaphore lets every body run at once"
    (let [latch (CountDownLatch. 4)
          met (atom 0)]
      (run-threads 4
                   #(core/with-permit nil
                                      (fn []
                                        (.countDown latch)
                                        (when (.await latch 2 TimeUnit/SECONDS)
                                          (swap! met inc)))))
      (is (= 4 @met))))
  (testing "the permit is returned when the body throws"
    (let [semaphore (Semaphore. 1 true)]
      (is (thrown? Exception
                   (core/with-permit semaphore
                                     ;; nosemgrep: no-raw-throw — a test
                                     (fn [] (throw (ex-info "boom" {}))))))
      (is (= 1 (.availablePermits semaphore))))))

(deftest parse-permits-test
  (is (= 6 (core/parse-permits "6")))
  (is (= 3 (core/parse-permits " 3 ")))
  (is (nil? (core/parse-permits "0")))
  (is (nil? (core/parse-permits "-1")))
  (is (nil? (core/parse-permits "six")))
  (is (nil? (core/parse-permits nil))))
