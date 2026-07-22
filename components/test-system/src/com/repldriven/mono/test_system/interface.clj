(ns com.repldriven.mono.test-system.interface
  "Test scaffolding macros for systems under `clojure.test`.
  `with-test-system` boots a system from a test config (with
  optional defs patcher) and tears it down on exit; `nom-test>`
  asserts a series of bindings are anomaly-free, printing any
  captured stack trace before failing."
  (:require
    [com.repldriven.mono.test-system.core :as core]))

(defmacro nom-test>
  "Run anomaly-checked `bindings` (as for `let-nom>`); on the first
  anomaly, print its stack trace and fail the surrounding `is`."
  {:clj-kondo/lint-as 'clojure.core/let}
  [bindings]
  `(core/nom-test> ~bindings))

(defmacro with-test-system
  "Bind `sym` to a started system from `config-file` (test profile)
  for the body's duration, asserting it started successfully and
  stopping it on exit. `config` may be a config-file ref or a
  `[config-file patch-fn]` pair where `patch-fn` rewrites the defs
  before start."
  {:clj-kondo/lint-as 'clojure.core/let}
  [binding & body]
  `(core/with-test-system ~binding ~@body))
