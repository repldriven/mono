(ns com.repldriven.mono.error.interface
  "Anomaly model and exception-translating macros built on top of
  `de.otto.nom`. Three anomaly tags — `:error/anomaly` for system
  faults, `:rejection/anomaly` for caller errors,
  `:unauthorized/anomaly` for authn/authz — share one
  `[tag kind payload]` shape; macros short-circuit threading and
  let-bindings on any of them."
  (:require
    [clojure.string :as str]
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

(defn anomaly?
  "True if `x` is any of the three anomaly shapes."
  [x]
  (or (error-anomaly? x) (rejection-anomaly? x) (unauthorized-anomaly? x)))

(defn error?
  "True if `x` is an `:error/anomaly`."
  [x]
  (error-anomaly? x))

(defn rejection?
  "True if `x` is a `:rejection/anomaly`."
  [x]
  (rejection-anomaly? x))

(defn unauthorized?
  "True if `x` is an `:unauthorized/anomaly`."
  [x]
  (unauthorized-anomaly? x))

(defn- anomaly
  [tag category & more]
  (let [p (cond
           (map? (first more))
           (first more)

           (string? (first more))
           {:message (first more)}

           (seq more)
           (apply hash-map more)

           :else
           {})]
    [tag category p]))

(defn fail
  "Construct an `:error/anomaly` with `category` (a kind keyword)
  and an optional payload (map, message string, or kv pairs)."
  [category & more]
  (apply anomaly :error/anomaly category more))

(defn reject
  "Construct a `:rejection/anomaly` with `category` and an optional
  payload (map, message string, or kv pairs)."
  [category & more]
  (apply anomaly :rejection/anomaly category more))

(defn unauthorized
  "Construct an `:unauthorized/anomaly` with `category` and an
  optional payload (map, message string, or kv pairs)."
  [category & more]
  (apply anomaly :unauthorized/anomaly category more))

(defn tag
  "Tag (first element) of an anomaly, or nil if `x` isn't one."
  [x]
  (when (anomaly? x) (first x)))

(defn kind
  "Kind keyword (second element) of an anomaly, or nil if `x` isn't one."
  [x]
  (when (anomaly? x) (second x)))

(defn payload
  "Payload map (third element) of an anomaly, or nil if `x` isn't one."
  [x]
  (when (anomaly? x) (get x 2 {})))

(defn format-anomaly
  "Render an anomaly as a multi-line string for operator logs:
  kind, message, underlying exception message (if distinct), and
  any captured stack trace.

  Args:
  - anomaly: an anomaly value."
  [anomaly]
  (let [{:keys [message exception stack-trace]} (payload anomaly)
        ex-msg (when exception (.getMessage ^Throwable exception))]
    (->> [(str "[" (kind anomaly)
               "]"
               (when message (str " " message)))
          (when (and ex-msg (not= ex-msg message))
            (str "  caused by: " ex-msg))
          stack-trace]
         (remove nil?)
         (str/join "\n"))))

(defmacro nom->
  "Anomaly-aware `->`. Threads `forms` left to right; if any step
  returns an anomaly, the rest are skipped and the anomaly is
  returned."
  {:clj-kondo/lint-as 'clojure.core/->}
  [& forms]
  `(nom/nom-> ~@forms))

(defmacro nom->>
  "Anomaly-aware `->>`."
  {:clj-kondo/lint-as 'clojure.core/->>}
  [& forms]
  `(nom/nom->> ~@forms))

(defmacro let-nom
  "Like `let`, but if any binding evaluates to an anomaly the
  remaining bindings + body are skipped and the anomaly is
  returned. Use when you don't need to short-circuit on a
  per-binding basis (compare `let-nom>`)."
  {:clj-kondo/lint-as 'clojure.core/let}
  [bindings & body]
  `(nom/let-nom ~bindings ~@body))

(defmacro let-nom>
  "Like `let`, but each binding is anomaly-checked: any anomalous
  binding short-circuits the rest of the let and returns that
  anomaly."
  {:clj-kondo/lint-as 'clojure.core/let}
  [bindings & body]
  `(nom/let-nom> ~bindings ~@body))

(defmacro with-nom
  "Wrap `body` in nom's anomaly-aware context. Mostly an
  implementation detail of the threading macros."
  [& body]
  `(nom/with-nom ~@body))

(defmacro try-nom
  "Catch any Exception thrown by `body` and convert it to an
  `:error/anomaly` with `category` as the kind, `message` as the
  payload `:message`, and `:exception` + `:stack-trace` capturing
  the original throwable."
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
  "Like `try-nom` but catches only a specific exception type."
  [category exception-type message & body]
  `(try ~@body
        (catch ~exception-type e#
          (fail ~category {:message ~message :exception e#}))))

(defmacro nom-do>
  "Execute `ops` sequentially, short-circuiting on the first
  anomaly. If any returns an anomaly, call `error-fn` with it."
  {:clj-kondo/lint-as 'clojure.core/->}
  [ops error-fn]
  (let [bindings (vec (mapcat (fn [op] [`_# op]) ops))]
    `(let [result# (nom/let-nom ~bindings :ok)]
       (when (anomaly? result#) (~error-fn result#)))))

(defmacro nom-let>
  "Execute `let-nom>` bindings (every binding anomaly-checked).
  If the result is an anomaly, call `error-fn` with it. Returns
  the result regardless."
  {:clj-kondo/lint-as 'clojure.core/let}
  [bindings error-fn]
  `(let [result# (nom/let-nom> ~bindings nil)]
     (when (anomaly? result#) (~error-fn result#))
     result#))
