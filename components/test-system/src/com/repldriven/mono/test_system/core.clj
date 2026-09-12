(ns com.repldriven.mono.test-system.core
  (:require
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error :refer [nom-let> nom->]]
    [com.repldriven.mono.system.interface :as system]
    [clojure.string :as str]
    [clojure.test :refer [is]])
  (:import
    (java.util.concurrent Semaphore)))

(defn parse-permits
  "A positive permit count from `s`, or nil for no bound"
  [s]
  (when s
    (let [n (parse-long (str/trim s))]
      (when (and n (pos? n)) n))))

(def permits
  "One JVM-wide semaphore sized by TEST_SYSTEM_PERMITS, or nil when the
  variable is unset, which leaves the number of test systems up at once
  unbounded."
  (delay (some-> (System/getenv "TEST_SYSTEM_PERMITS")
                 parse-permits
                 int
                 (Semaphore. true))))

(defn with-permit
  "Call `f` holding one of `semaphore`'s permits, waiting for one if
  none is free, or straight away when `semaphore` is nil."
  [^Semaphore semaphore f]
  (if semaphore
    (do (.acquire semaphore)
        (try (f) (finally (.release semaphore))))
    (f)))

(defmacro nom-test>
  {:clj-kondo/lint-as 'clojure.core/let}
  [bindings]
  `(nom-let> ~bindings
             (fn [v#]
               (let [payload# (error/payload v#)]
                 (when-let [st# (:stack-trace payload#)] (println st#))
                 (is (not (error/anomaly? v#))
                     (format "Unexpected anomaly [%s]: %s"
                             (error/kind v#)
                             (or (:message payload#) (pr-str v#))))))))

(defmacro with-test-system
  {:clj-kondo/lint-as 'clojure.core/let}
  [[sym config] & body]
  (let [[config-file patch-fn] (if (vector? config) config [config nil])
        start
        (if patch-fn
          `(nom-> (env/config ~config-file :test)
                  system/defs
                  ~(list patch-fn)
                  system/start)
          `(nom-> (env/config ~config-file :test) system/defs system/start))]
    `(with-permit
      @permits
      (fn []
        (system/with-system [~sym ~start] (is (system/system? ~sym)) ~@body)))))
