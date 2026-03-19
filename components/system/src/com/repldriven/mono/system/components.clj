(ns com.repldriven.mono.system.components
  (:require
    [com.repldriven.mono.utility.interface :as utility]))

(defn merge-component-config
  [component config]
  (update component
          :system/config
          (fn [original]
            (utility/deep-merge original
                                (dissoc config :system/component-kind)))))

(defmulti component
  "Component configuration multimethod.
  Dispatches on the component kind.
  Components should extend this to register themselves."
  (fn [_ v] (keyword (:system/component-kind v))))

(defmethod component :default [_ v] v)

(defmacro defcomponents
  "Defines multiple system/component defmethods for a given namespace.

  Usage:
    (defcomponents :server
      {:interceptors components/interceptors
       :jetty-adapter components/jetty-adapter})

  Expands to:
    (defmethod component :server/interceptors [_ v]
      (merge-component-config components/interceptors v))
    (defmethod component :server/jetty-adapter [_ v]
      (merge-component-config components/jetty-adapter v))"
  [ns-keyword component-map]
  `(do ~@(for [[component-name component-def] component-map]
           `(defmethod component ~(keyword (name ns-keyword)
                                           (name component-name))
              [~'_ ~'v]
              (merge-component-config ~component-def ~'v)))))

(defn- component-group
  "Processes a component group by reducing over its components."
  [group-config]
  (reduce-kv (fn [components component-name component-config]
               (assoc components
                      component-name
                      (component component-name component-config)))
             {}
             group-config))

(defn defs
  "Builds system definitions by reducing over component groups and their components.
  Takes a config map and optional path (defaults to [:system]) to extract system config."
  ([config] (defs config [:system]))
  ([config ks]
   {:system/defs (reduce-kv (fn [groups group-name group-config]
                              (assoc groups
                                     group-name
                                     (component-group group-config)))
                            {}
                            (get-in config ks))}))
