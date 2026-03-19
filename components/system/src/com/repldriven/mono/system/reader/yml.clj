(ns com.repldriven.mono.system.reader.yml
  (:refer-clojure :exclude [ref])
  (:require
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.utility.interface :as util]
    [clojure.string :as str]))

(defn local-ref
  [{:keys [value]}]
  (let [ks (str/split value #"\.")]
    (symbol (str "[#system local-ref " (pr-str (mapv keyword ks)) "]"))))

(defn ref
  [{:keys [value]}]
  (let [ks (str/split value #"\.")]
    (symbol (str "[#system ref " (pr-str (mapv keyword ks)) "]"))))

(defn required-component [_] (symbol "#system required-component"))

;; System yml-reader defmethods
(defmethod env/yml-reader :!system/required-component
  [m]
  (required-component m))

(defmethod env/yml-reader :!system/ref [m] (ref m))

(defmethod env/yml-reader :!system/local-ref [m] (local-ref m))

(defmethod env/yml-reader :!system/component
  [{:keys [value]}]
  (let [value-map (util/yaml-collections->edn-collections value)
        component-kind (get value-map :system/component-kind)
        config (dissoc value-map :system/component-kind)
        config-edn (into {} (map (fn [[k v]] [(keyword k) v]) config))]
    (assoc config-edn :system/component-kind (keyword component-kind))))
