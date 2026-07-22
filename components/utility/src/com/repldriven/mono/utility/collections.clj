(ns com.repldriven.mono.utility.collections
  (:require
    [clojure.walk :refer [postwalk]]))

(defn yaml-collections->edn-collections
  [form]
  (postwalk #(cond (= "class flatland.ordered.map.OrderedMap" (str (type %)))
                   (into (hash-map) %)
                   (seq? %)
                   (into (vector) %)
                   :else
                   %)
            form))

(defn deep-merge
  [& values]
  (if (every? map? values) (apply merge-with deep-merge values) (last values)))

(defn val-strs->keywords
  [form]
  (postwalk
   #(if (map? %)
      (into (hash-map) (map (fn [[k v]] [k (if (string? v) (keyword v) v)]) %))
      %)
   form))

(defn keys->strs
  [form]
  (postwalk #(if (map? %) (into (hash-map) (map (fn [[k v]] [(name k) v]) %)) %)
            form))

(defn record->map
  [form]
  (postwalk #(if (record? %) (into {} %) %) form))

(defn assoc-some
  [m & kvs]
  (reduce (fn [acc [k v]] (if (some? v) (assoc acc k v) acc))
          m
          (partition 2 kvs)))

(defn assoc-seq
  [m & kvs]
  (reduce (fn [acc [k v]] (if (not-empty v) (assoc acc k v) acc))
          m
          (partition 2 kvs)))

(defn deep-some
  [pred coll]
  (cond
   (pred coll)
   [[] coll]

   (map? coll)
   (some (fn [[k v]]
           (when-let [[p m] (deep-some pred v)]
             [(into [k] p) m]))
         coll)

   (sequential? coll)
   (some (fn [[i v]]
           (when-let [[p m] (deep-some pred v)]
             [(into [i] p) m]))
         (map-indexed vector coll))

   (set? coll)
   (some #(deep-some pred %) coll)

   :else
   nil))
