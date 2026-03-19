(ns com.repldriven.mono.cache.interface
  (:require
    [com.repldriven.mono.cache.core :as core]))

(defn create
  "Returns an atom wrapping a TTL cache with the given
  expiry in milliseconds."
  [ttl-ms]
  (core/create ttl-ms))

(defn lookup
  "Returns cached value for k if present. Otherwise calls
  (miss-fn), caches and returns the result. nil results from
  miss-fn are not cached."
  [cache-atom k miss-fn]
  (core/lookup cache-atom k miss-fn))

(defn evict
  "Removes the entry for k from the cache."
  [cache-atom k]
  (core/evict cache-atom k))
