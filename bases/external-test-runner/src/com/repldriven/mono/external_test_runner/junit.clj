(ns com.repldriven.mono.external-test-runner.junit
  "JUnit XML reporter with suite-level timing and trimmed namespace names.

  Replaces eftest.report.junit to add:
  - time attribute on <testsuite> elements (fixes NaN in CI reports)
  - strips com.repldriven.mono. prefix from suite/classname attributes"
  (:require
    [clojure.stacktrace :as stack]
    [eftest.report :refer [*context*]]
    [clojure.test :as test]))

(set! *warn-on-reflection* true)

(def ^:private ns-prefix "com.repldriven.mono.")

(defn- trim-ns
  [^String ns-str]
  (if (.startsWith ns-str ns-prefix)
    (.substring ns-str (count ns-prefix))
    ns-str))

(def ^:private flush-lock (Object.))

(def ^:private escape-xml-map
  (zipmap "'<>\"&" (map #(str \& % \;) '[apos lt gt quot amp])))

(defn- escape-xml [text] (apply str (map #(escape-xml-map % %) text)))

(defn- start-element
  [tag attrs]
  (print (str "<" tag))
  (doseq [[k v] attrs]
    (print (str " " (name k) "=\"" (escape-xml (str v)) "\"")))
  (print ">"))

(defn- finish-element [tag] (print (str "</" tag ">")))

(defn- element-content [content] (print (escape-xml content)))

(defn- test-name
  [vars]
  (apply str (interpose "." (reverse (map #(:name (meta %)) vars)))))

(defn- message-el
  [tag {:keys [message expected actual file line]}]
  (start-element tag (if message {:message message} {}))
  (element-content (let [detail (apply str
                                       (interpose "\n"
                                        [(str "expected: " (pr-str expected))
                                         (str "  actual: "
                                              (if (instance? Throwable actual)
                                                (with-out-str
                                                  (stack/print-cause-trace
                                                   actual
                                                   test/*stack-trace-depth*))
                                                (pr-str actual)))
                                         (str "      at: " file ":" line)]))]
                     (if message (str message "\n" detail) detail)))
  (finish-element tag)
  (println))

(defn- combine [f g] (fn [] (g) (f)))

(defn- push-result
  [result]
  (let [test-var (first test/*testing-vars*)]
    (swap! *context* update-in [::test-results test-var] conj result)))

(defmulti report :type)

(defmethod report :default [_])

(defmethod report :begin-test-run
  [_]
  (swap! *context* assoc ::test-results {})
  (test/with-test-out (println "<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                      (print "<testsuites>")))

(defmethod report :summary [_] (test/with-test-out (println "</testsuites>")))

(defmethod report :begin-test-ns
  [m]
  (let [ns-str (name (ns-name (:ns m)))
        start-time (System/nanoTime)
        f #(test/with-test-out
            (start-element 'testsuite
                           {:name (trim-ns ns-str)
                            :time (format "%.03f"
                                          (/ (- (System/nanoTime) start-time)
                                             1e9))}))]
    (swap! *context* assoc-in [::deferred-report ns-str] f)))

(defmethod report :end-test-ns
  [m]
  (let [ns-str (name (ns-name (:ns m)))
        g (get-in @*context* [::deferred-report ns-str])
        f #(test/with-test-out (finish-element 'testsuite))]
    (locking flush-lock (g) (f))
    (swap! *context* update ::deferred-report dissoc ns-str)))

(defmethod report :begin-test-var
  [m]
  (swap! *context* assoc-in [::test-start-times (:var m)] (System/nanoTime)))

(defmethod report :end-test-var
  [m]
  (let [ns-str (-> (:var m)
                   meta
                   :ns
                   ns-name
                   name)
        duration (- (System/nanoTime)
                    (get-in @*context* [::test-start-times (:var m)]))
        testing-vars test/*testing-vars*
        f #(test/with-test-out
            (let [test-var (:var m)
                  time-str (format "%.03f" (/ duration 1e9))
                  results (get-in @*context* [::test-results test-var])]
              (start-element 'testcase
                             {:name (test-name testing-vars)
                              :classname (trim-ns ns-str)
                              :time time-str})
              (doseq [result results]
                (if (= :fail (:type result))
                  (message-el 'failure result)
                  (message-el 'error result)))
              (finish-element 'testcase)
              (println)
              (swap! *context* update ::test-results dissoc test-var)))]
    (swap! *context* update-in [::deferred-report ns-str] (partial combine f))))

(defmethod report :pass [_] (test/inc-report-counter :pass))

(defmethod report :fail [m] (test/inc-report-counter :fail) (push-result m))

(defmethod report :error [m] (test/inc-report-counter :error) (push-result m))
