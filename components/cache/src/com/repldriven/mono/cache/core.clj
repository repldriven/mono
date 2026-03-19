(ns com.repldriven.mono.cache.core
  (:require
    [clojure.core.cache.wrapped :as cw]))

(defn create [ttl-ms] (cw/ttl-cache-factory {} :ttl ttl-ms))

(defn lookup
  [cache-atom k miss-fn]
  (if (cw/has? cache-atom k)
    (cw/lookup cache-atom k)
    (let [v (miss-fn)]
      (when (some? v) (cw/miss cache-atom k v))
      v)))

(defn evict [cache-atom k] (cw/evict cache-atom k))
