(ns com.repldriven.mono.system.core
  (:refer-clojure :exclude [ref])
  (:require
    [com.repldriven.mono.error.interface :as error :refer [try-nom]]
    [com.repldriven.mono.utility.interface :as util]
    [donut.system :as ds]
    [donut.system.validation :as dsv]))

(def mono-system-ns "system")
(def donut-system-ns "donut.system")

(defn- match-ns-keyword?
  [x match-ns]
  (and (keyword? x) (= match-ns (namespace x))))

(defn- schema-key-name? [k] (and (keyword? k) (.endsWith (name k) "-schema")))

(defn- wrap-fn
  [f from-ns to-ns]
  (fn [to-ns-map]
    (let [args (reduce-kv (fn [m k v]
                            (assoc m
                                   (if (match-ns-keyword? k to-ns)
                                     (keyword from-ns (name k))
                                     k)
                                   v))
                          {}
                          to-ns-map)
          result (f args)]
      (if-let [[path anomaly] (util/deep-some error/anomaly? result)]
        ;; nosemgrep: no-raw-throw
        (throw (ex-info (or (:message (error/payload anomaly))
                            "Component lifecycle returned an anomaly")
                        {:kind (error/kind anomaly)
                         :path path
                         :anomaly anomaly
                         :payload (error/payload anomaly)}))
        result))))

(defn- nsmap->nsmap
  [m from-ns to-ns]
  (letfn
    [(walk [x]
       (cond (map? x)
             (into {}
                   (map (fn [[k v]]
                          (let [k' (walk k)]
                            (if (schema-key-name? k')
                              [k' v]
                              [k' (walk v)]))))
                   x)
             (vector? x)
             (mapv walk x)
             (seq? x)
             (doall (map walk x))
             (set? x)
             (into #{} (map walk x))
             (match-ns-keyword? x from-ns)
             (keyword to-ns (name x))
             (fn? x)
             (wrap-fn x from-ns to-ns)
             :else
             x))]
    (walk m)))

(def required-component ::ds/required-component)

(defn system?
  [config-name]
  (ds/system? (nsmap->nsmap config-name mono-system-ns donut-system-ns)))

(defn- with-validation
  [system-map]
  (update system-map ::ds/plugins (fnil conj []) dsv/validation-plugin))

(defn start
  ([config-name]
   (try-nom :system/start
            "System START threw an exception"
            (ds/start (-> config-name
                          (nsmap->nsmap mono-system-ns donut-system-ns)
                          with-validation))))
  ([config-name custom-config]
   (try-nom :system/start
            "System START threw an exception"
            (ds/start (-> config-name
                          (nsmap->nsmap mono-system-ns donut-system-ns)
                          with-validation)
                      custom-config)))
  ([config-name custom-config component-ids]
   (try-nom :system/start
            "System START threw an exception"
            (ds/start (-> config-name
                          (nsmap->nsmap mono-system-ns donut-system-ns)
                          with-validation)
                      custom-config
                      component-ids))))

(defn instance [system kws] (get-in system (vec (cons ::ds/instances kws))))

(defn config
  [system group component]
  (get-in system [::ds/defs group component ::ds/config]))

(defn stop
  [system]
  (try-nom :system/stop
           "System STOP threw an exception"
           ;; Return nil, not the stopped system map: it holds every
           ;; started instance (e.g. the full Lancaster schema set) and
           ;; floods a REPL that prints the result.
           (do (ds/stop system) nil)))

(defmacro with-system
  {:clj-kondo/lint-as 'clojure.core/let}
  [binding & body]
  (let [[sym init] binding]
    `(let [~sym ~init]
       (try ~@body (finally (when-not (error/anomaly? ~sym) (stop ~sym)))))))
