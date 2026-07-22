(ns com.repldriven.mono.test-system.core
  (:require
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error :refer [nom-let> nom->]]
    [com.repldriven.mono.system.interface :as system]
    [clojure.test :refer [is]]))

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
  (let [[config-file patch-fn] (if (vector? config) config [config nil])]
    (if patch-fn
      `(system/with-system [~sym
                            (nom-> (env/config ~config-file :test)
                                   system/defs
                                   ~(list patch-fn)
                                   system/start)]
         (is (system/system? ~sym))
         ~@body)
      `(system/with-system
         [~sym (nom-> (env/config ~config-file :test) system/defs system/start)]
         (is (system/system? ~sym))
         ~@body))))
