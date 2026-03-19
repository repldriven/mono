(ns com.repldriven.mono.error.interface
  (:require
    [de.otto.nom.core :as nom]))

(defn- error-anomaly? [x] (and (vector? x) (= :error/anomaly (first x))))
(defn- rejection-anomaly?
  [x]
  (and (vector? x) (= :rejection/anomaly (first x))))
(defn- unauthorized-anomaly?
  [x]
  (and (vector? x) (= :unauthorized/anomaly (first x))))

(defmethod nom/abominable? error-anomaly? [_] true)
(defmethod nom/abominable? rejection-anomaly? [_] true)
(defmethod nom/abominable? unauthorized-anomaly? [_] true)

(defmethod nom/adapt error-anomaly? [x] x)
(defmethod nom/adapt rejection-anomaly? [x] x)
(defmethod nom/adapt unauthorized-anomaly? [x] x)

;; Predicates
(defn anomaly?
  [x]
  (or (error-anomaly? x) (rejection-anomaly? x) (unauthorized-anomaly? x)))
(defn error? [x] (error-anomaly? x))
(defn rejection? [x] (rejection-anomaly? x))
(defn unauthorized? [x] (unauthorized-anomaly? x))

;; Internal constructors
(defn- anomaly
  [tag category & more]
  (let [p (cond (map? (first more))
                (first more)
                (string? (first more))
                {:message (first more)}
                (seq more)
                (apply hash-map more)
                :else
                {})]
    [tag category p]))

;; Public constructors
(defn fail [category & more] (apply anomaly :error/anomaly category more))
(defn reject [category & more] (apply anomaly :rejection/anomaly category more))
(defn unauthorized
  [category & more]
  (apply anomaly :unauthorized/anomaly category more))

;; Introspection
(defn tag [x] (when (anomaly? x) (first x)))
(defn kind [x] (when (anomaly? x) (second x)))
(defn payload [x] (when (anomaly? x) (get x 2 {})))

;; Threading macros
(defmacro nom->
  {:clj-kondo/lint-as 'clojure.core/->}
  [& forms]
  `(nom/nom-> ~@forms))

(defmacro nom->>
  {:clj-kondo/lint-as 'clojure.core/->>}
  [& forms]
  `(nom/nom->> ~@forms))

;; Let bindings
(defmacro let-nom
  {:clj-kondo/lint-as 'clojure.core/let}
  [bindings & body]
  `(nom/let-nom ~bindings ~@body))

(defmacro let-nom>
  {:clj-kondo/lint-as 'clojure.core/let}
  [bindings & body]
  `(nom/let-nom> ~bindings ~@body))

;; Context wrapper
(defmacro with-nom [& body] `(nom/with-nom ~@body))

;; Exception catching
(defmacro try-nom
  [category message & body]
  `(try ~@body
        (catch Exception e#
          (update (fail ~category ~message)
                  2 assoc
                  :exception e#
                  :stack-trace
                  (with-out-str
                    (.printStackTrace e# (java.io.PrintWriter. *out* true)))))))

(defmacro try-nom-ex
  "Like try-nom but catches a specific exception type."
  [category exception-type message & body]
  `(try ~@body
        (catch ~exception-type e#
          (fail ~category {:message ~message :exception e#}))))

;; Side-effect error handling
(defmacro nom-do>
  "Execute operations sequentially, short-circuiting on the first anomaly.
  If any returns an anomaly, call error-fn with it."
  [ops error-fn]
  (let [bindings (vec (mapcat (fn [op] [`_# op]) ops))]
    `(let [result# (nom/let-nom ~bindings :ok)]
       (when (anomaly? result#) (~error-fn result#)))))

(defmacro nom-let>
  "Execute let-nom> bindings (every binding anomaly-checked). If the result is
  an anomaly, call error-fn with it. Returns the result."
  [bindings error-fn]
  `(let [result# (nom/let-nom> ~bindings nil)]
     (when (anomaly? result#) (~error-fn result#))
     result#))
