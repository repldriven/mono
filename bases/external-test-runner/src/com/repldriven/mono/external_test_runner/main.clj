(ns com.repldriven.mono.external-test-runner.main
  (:require
    [cloverage.coverage :as cov]
    [cloverage.instrument :as inst]
    [cloverage.report :as rep]
    [eftest.report :refer [report-to-file]]
    [eftest.report.pretty :as report]
    [com.repldriven.mono.external-test-runner.junit :as junit]
    [eftest.runner :as eftest]
    [clojure.java.io :as io]
    [clojure.string :as str]
    clojure.test)
  (:import
    (java.util.concurrent Semaphore))
  (:gen-class))

(set! *warn-on-reflection* true)

(def cli-options
  [["-c" "--color-mode MODE" "Color mode (none, light, dark)" :default :none
    :parse-fn keyword]
   ["-p" "--project PROJECT" "Project name" :default "unknown"]
   ["-v" "--verbose" "Verbose output" :default false]
   ["-s" "--skip-meta META" "Skip tests with metadata (e.g., :integration)"
    :parse-fn keyword :assoc-fn (fn [m k v] (update m k (fnil conj []) v))]
   ["-f" "--focus-meta META" "Only run tests with metadata (e.g., :integration)"
    :parse-fn keyword :assoc-fn (fn [m k v] (update m k (fnil conj []) v))]
   ["-h" "--help" "Show help"]])

(defn- parse-env-nses
  [env-var]
  (when-let [s (System/getenv env-var)]
    (when-not (str/blank? s) (mapv symbol (str/split s #",")))))

(defn- default-synchronized-permits
  "The JVM's processor count, which -XX:ActiveProcessorCount caps"
  []
  (.availableProcessors (Runtime/getRuntime)))

(defn- parse-env-permits
  "Parse a positive permit count from an environment variable, or nil"
  [env-var]
  (when-let [s (System/getenv env-var)]
    (let [n (parse-long (str/trim s))]
      (when (and n (pos? n)) n))))

(defn- parse-env-meta
  "Parse comma-separated metadata keywords from environment variable"
  [env-var]
  (when-let [meta-str (System/getenv env-var)]
    (when-not (str/blank? meta-str) (mapv keyword (str/split meta-str #",")))))

(defn- build-selector
  "Build a test selector function based on skip-meta and focus-meta"
  [skip-meta focus-meta]
  (let [skip-set (set skip-meta)
        focus-set (set focus-meta)]
    (cond
     ;; If focus-meta is specified, only run tests with those tags
     (seq focus-set)
     (fn [test-var]
       (let [test-meta (meta test-var)]
         (some focus-set (keys test-meta))))
     ;; If skip-meta is specified, skip tests with those tags
     (seq skip-set)
     (fn [test-var]
       (let [test-meta (meta test-var)]
         (not (some skip-set (keys test-meta)))))
     ;; Otherwise, run all tests
     :else
     (constantly true))))

(defn- require-test-namespaces
  "Require all test namespaces"
  [test-nses]
  (doseq [ns-sym test-nses]
    (try (require ns-sym)
         (catch Exception e
           (println (format "Failed to require namespace %s: %s"
                            ns-sym
                            (.getMessage e)))
           ;; nosemgrep: no-raw-throw
           (throw e)))))

(defn- find-test-vars
  "Find all test vars in the specified namespaces"
  [test-nses]
  (require-test-namespaces test-nses)
  (->> test-nses
       (mapcat (fn [ns-sym]
                 (let [ns-obj (find-ns ns-sym)]
                   (->> (ns-publics ns-obj)
                        vals
                        (filter (fn [v] (:test (meta v))))))))
       vec))

(defn- synchronized-ns?
  [ns]
  (true? (:eftest/synchronized (meta ns))))

(defn synchronize-namespaces!
  "Give `^:eftest/synchronized` its meaning when namespaces run in parallel.

  eftest honours the marker only in `:vars` mode; in `:namespaces` mode it
  submits every namespace to an unbounded pool at once and ignores it. This
  installs an outermost once-fixture on each marked namespace among `vars`
  that holds one of `permits` for the namespace's whole run, so at most
  `permits` marked namespaces run at a time while unmarked ones run freely.
  Returns `vars`.

  The once-fixture slot is the runner's hook, not a test's: no namespace
  declares it and no lifecycle runs in it. It is the one place eftest runs
  code around a whole namespace on that namespace's own thread, before the
  per-test timer starts and outside the lock every report event takes."
  [vars permits]
  (let [semaphore (Semaphore. (int permits) true)
        fixture (fn [f]
                  (.acquire semaphore)
                  (try (f) (finally (.release semaphore))))]
    (doseq [ns (->> vars
                    (map (comp :ns meta))
                    distinct
                    (filter synchronized-ns?))]
      (alter-meta! ns update :clojure.test/once-fixtures #(into [fixture] %)))
    vars))

(defn- make-junit-reporter
  "Create a JUnit XML reporter that writes to target/test-results/junit.xml"
  []
  (let [output-dir (io/file "target" "test-results")
        output-file (io/file output-dir "junit.xml")]
    (.mkdirs output-dir)
    (report-to-file junit/report (.getPath output-file))))

(defn- multi-reporter
  "Create a reporter that outputs to both stdout and JUnit XML.
   Binds *report-counters* to nil for JUnit to prevent double-counting."
  [junit-reporter]
  (fn [event]
    (report/report event)
    (binding [clojure.test/*report-counters* nil] (junit-reporter event))))

(defn- run-test-namespaces
  "Run tests in the specified namespaces using eftest"
  [test-nses {:keys [verbose skip-meta focus-meta synchronized-permits]}]
  (when verbose
    (println (format "Running tests in %d namespace%s"
                     (count test-nses)
                     (if (= 1 (count test-nses)) "" "s")))
    (when (seq skip-meta) (println "Skipping tests with metadata:" skip-meta))
    (when (seq focus-meta)
      (println "Focusing on tests with metadata:" focus-meta)))
  (let [all-test-vars (find-test-vars test-nses)
        selector (build-selector skip-meta focus-meta)
        filtered-test-vars (synchronize-namespaces!
                            (filterv selector all-test-vars)
                            (or synchronized-permits
                                (default-synchronized-permits)))
        junit-reporter (make-junit-reporter)
        combined-reporter (multi-reporter junit-reporter)]
    (eftest/run-tests filtered-test-vars
                      {:capture-output? false
                       :multithread? :namespaces
                       :report combined-reporter
                       :test-warn-time 1000})))

(defn- coverage-pct
  "Calculate overall line coverage percentage from gathered stats"
  [forms]
  (let [tracked (filter :tracked forms)
        total (count tracked)
        hit (count (filter :covered tracked))]
    (if (pos? total) (* 100.0 (/ hit total)) 0.0)))

(defn- run-with-coverage
  "Instrument src namespaces, run tests, emit coverage report.

  Env vars:
    COVERAGE_THRESHOLD - minimum coverage % (default: none)"
  [test-nses src-nses project opts]
  (let [src-ns-syms (or src-nses
                        ;; fallback: derive from test nses by convention
                        ;; com.repldriven.mono.foo.core-test ->
                        ;; com.repldriven.mono.foo.core
                        (mapv #(-> (str %)
                                   (str/replace #"-test$" "")
                                   symbol)
                              test-nses))
        threshold (some-> (System/getenv "COVERAGE_THRESHOLD")
                          parse-double)]
    (println "Coverage: instrumenting" (count src-ns-syms) "namespaces")
    ;; 1. Reset tracking atom
    (reset! cov/*covered* [])
    ;; 2. Require then instrument each src namespace
    (doseq [ns-sym src-ns-syms]
      (if-let [_ (try (require ns-sym)
                      ns-sym
                      (catch java.io.FileNotFoundException _ nil))]
        (do (binding [cov/*instrumented-ns* ns-sym]
              (inst/instrument #'cov/track-coverage ns-sym))
            (cov/mark-loaded ns-sym)
            (println "Instrumented" ns-sym))
        (println "Skipping" ns-sym "(not found)")))
    ;; 3. Run tests via existing eftest runner
    (let [results (run-test-namespaces test-nses opts)
          ;; 4. Collect and report coverage
          forms (rep/gather-stats (cov/covered))
          pct (coverage-pct forms)]
      (cov/report-results
       {:output (str "target/coverage/" project) :html? true :lcov? true}
       {}
       forms)
      (println (format "Coverage: %.1f%%" pct))
      (when (and threshold (< pct threshold))
        (println
         (format "FAIL: coverage %.1f%% below threshold %.1f%%" pct threshold))
        (System/exit 1))
      results)))

(defn- exit-with-results
  "Exit with appropriate code based on test results"
  [{:keys [fail error]}]
  (let [failures (+ (or fail 0) (or error 0))]
    (System/exit (if (zero? failures) 0 1))))

(defn -main
  "Main entry point for external test runner subprocess

  Called by Corfield external-test-runner with positional args:
    color-mode project-name test-namespace [test-namespace ...]

  Supports metadata-based test filtering via environment variables:
  - ENV: SKIP_META=integration,slow FOCUS_META=unit

  EFTEST_SYNCHRONIZED_PERMITS is how many `^:eftest/synchronized`
  namespaces may run at once. The default is the JVM's processor count,
  so -XX:ActiveProcessorCount bounds it along with everything else.

  JUNIT_NS_PREFIX trims that prefix off JUnit suite/classname attributes."
  [& args]
  (let [;; Parse positional args from Corfield runner: color-mode
        ;; project-name ...test-nses
        _color-mode (keyword (first args))
        project (second args)
        test-nses (map symbol (drop 2 args))
        ;; Get metadata filtering from environment variables
        env-skip (parse-env-meta "SKIP_META")
        env-focus (parse-env-meta "FOCUS_META")
        coverage? (= "true" (System/getenv "COVERAGE"))
        src-nses (parse-env-nses "COVERAGE_SRC_NSES")]
    (cond (empty? test-nses)
          (do (println "No test namespaces specified")
              (System/exit 1))
          :else
          (let [opts {:verbose false
                      :skip-meta env-skip
                      :focus-meta env-focus
                      :synchronized-permits
                      (or (parse-env-permits "EFTEST_SYNCHRONIZED_PERMITS")
                          (default-synchronized-permits))}
                results (if coverage?
                          (run-with-coverage test-nses src-nses project opts)
                          (run-test-namespaces test-nses opts))]
            (exit-with-results results)))))
