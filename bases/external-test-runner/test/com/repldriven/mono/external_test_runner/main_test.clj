(ns com.repldriven.mono.external-test-runner.main-test
  (:require
    [com.repldriven.mono.external-test-runner.main :as main]

    [eftest.runner :as eftest]
    [clojure.test :refer [deftest is testing]]))

(defn- overlap-ns
  "A namespace holding one test whose body counts how many of its
  siblings are running at the same time."
  [sym active peak synchronized?]
  (let [ns (create-ns sym)]
    (when synchronized? (alter-meta! ns assoc :eftest/synchronized true))
    (intern ns
            (with-meta 'overlap-test
                       {:test (fn []
                                (swap! peak max (swap! active inc))
                                (Thread/sleep 50)
                                (swap! active dec))})
            (fn []))
    ns))

(defn- run-overlapping!
  "Run `marked` synchronized namespaces and one unmarked one through
  eftest's namespace mode with `permits`, returning the peak number of
  marked namespaces that ran at once."
  [prefix marked permits]
  (let [active (atom 0)
        peak (atom 0)
        nses
        (conj
         (mapv #(overlap-ns (symbol (str prefix ".marked-" %))
                            active
                            peak
                            true)
               (range marked))
         (overlap-ns (symbol (str prefix ".free")) (atom 0) (atom 0) false))
        vars (mapv #(get (ns-publics %) 'overlap-test) nses)]
    (try (main/synchronize-namespaces! vars permits)
         (eftest/run-tests vars
                           {:multithread? :namespaces
                            :capture-output? false
                            :report (fn [_])})
         @peak
         (finally (run! (comp remove-ns ns-name) nses)))))

(deftest synchronize-namespaces-test
  (testing "marked namespaces run one at a time by default"
    (is (= 1 (run-overlapping! "sync.one" 4 1))))
  (testing "permits bound how many marked namespaces run at once"
    (is (<= (run-overlapping! "sync.two" 4 2) 2)))
  (testing "only marked namespaces get the fixture"
    (let [marked (overlap-ns 'sync.meta.marked (atom 0) (atom 0) true)
          free (overlap-ns 'sync.meta.free (atom 0) (atom 0) false)]
      (try
        (main/synchronize-namespaces! [(get (ns-publics marked) 'overlap-test)
                                       (get (ns-publics free) 'overlap-test)]
                                      1)
        (is (= 1 (count (:clojure.test/once-fixtures (meta marked)))))
        (is (nil? (:clojure.test/once-fixtures (meta free))))
        (finally (remove-ns 'sync.meta.marked) (remove-ns 'sync.meta.free))))))
