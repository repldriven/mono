(ns com.repldriven.mono.system.components
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.utility.interface :as utility]))

(defn merge-component-config
  [component config]
  (update component
          :system/config
          (fn [original]
            (utility/deep-merge original
                                (dissoc config :system/component-kind)))))

(defmulti component (fn [_ v] (keyword (:system/component-kind v))))

;; A value with no kind is configuration and passes through. A kind
;; nobody registered is refused here rather than built as a component
;; that does nothing: nothing would start it, its config would stand in
;; as its instance, and the failure would surface wherever that
;; instance is first used, far from the namespace that was not loaded.
(defmethod component :default
  [component-name v]
  (if-let [kind (:system/component-kind v)]
    (error/fail :system/unknown-component-kind
                {:message (str "No defcomponents registered the component kind "
                               kind)
                 :component component-name
                 :kind kind})
    v))

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
   (let [groups (reduce-kv (fn [groups group-name group-config]
                             (assoc groups
                                    group-name
                                    (component-group group-config)))
                           {}
                           (get-in config ks))]
     (if-let [[_ anomaly] (utility/deep-some error/anomaly? groups)]
       anomaly
       {:system/defs groups}))))
