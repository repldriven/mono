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

(defmulti component (fn [_ v] (keyword (:system/component-kind v))))

(defmethod component :default [_ v] v)

(defmacro defcomponents
  [ns-keyword component-map]
  `(do ~@(for [[component-name component-def] component-map]
           `(defmethod component ~(keyword (name ns-keyword)
                                           (name component-name))
              [~'_ ~'v]
              (merge-component-config ~component-def ~'v)))))

(defn- component-group
  [group-config]
  (reduce-kv (fn [components component-name component-config]
               (assoc components
                      component-name
                      (component component-name component-config)))
             {}
             group-config))

(defn defs
  ([config] (defs config [:system]))
  ([config ks]
   {:system/defs (reduce-kv (fn [groups group-name group-config]
                              (assoc groups
                                     group-name
                                     (component-group group-config)))
                            {}
                            (get-in config ks))}))
