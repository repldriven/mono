(ns com.repldriven.mono.test-system.interface
  (:require
    [com.repldriven.mono.test-system.core :as core]))

(defmacro nom-test>
  {:clj-kondo/lint-as 'clojure.core/let}
  [bindings]
  `(core/nom-test> ~bindings))

(defmacro with-test-system
  {:clj-kondo/lint-as 'clojure.core/let}
  [binding & body]
  `(core/with-test-system ~binding ~@body))

