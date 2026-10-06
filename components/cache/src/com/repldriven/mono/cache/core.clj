(ns com.repldriven.mono.cache.core
  (:require
    [clojure.core.cache :as c]
    [clojure.core.cache.wrapped :as cw]))

(defn create
  ([ttl-ms] (cw/ttl-cache-factory {} :ttl ttl-ms))
  ([ttl-ms max-entries]
   (atom (c/ttl-cache-factory (c/fifo-cache-factory {} :threshold max-entries)
                              :ttl
                              ttl-ms))))

(defn lookup
  [cache-atom k miss-fn]
  (if (cw/has? cache-atom k)
    (cw/lookup cache-atom k)
    (let [v (miss-fn)]
      (when (some? v) (cw/miss cache-atom k v))
      v)))

(defn evict [cache-atom k] (cw/evict cache-atom k))
