(ns com.repldriven.mono.cache.interface
  "TTL cache helpers built on `clojure.core.cache.wrapped`.
  Returns an atom-wrapped cache; callers `lookup` with a miss-fn
  to populate, or `evict` to drop a key. nil miss results are not
  cached."
  (:require
    [com.repldriven.mono.cache.core :as core]))

(defn create
  "Return an atom wrapping a TTL cache, holding at most `max-entries`
  when given, the oldest going first.

  Args:
  - ttl-ms: entry expiry in milliseconds.
  - max-entries: optional bound on the number of entries."
  ([ttl-ms] (core/create ttl-ms))
  ([ttl-ms max-entries] (core/create ttl-ms max-entries)))

(defn lookup
  "Return the cached value for `k`, or call `miss-fn`, cache its
  result (when non-nil), and return it.

  Args:
  - cache-atom: the cache atom returned by `create`.
  - k: the cache key.
  - miss-fn: thunk computing the value on a miss."
  [cache-atom k miss-fn]
  (core/lookup cache-atom k miss-fn))

(defn evict
  "Remove the entry for `k` from the cache.

  Args:
  - cache-atom: the cache atom returned by `create`.
  - k: the cache key."
  [cache-atom k]
  (core/evict cache-atom k))
