(ns com.repldriven.mono.utility.id
  (:require
    [com.repldriven.mono.utility.ulid :as ulid]))

(defn generate
  [prefix]
  (str prefix "." (ulid/monotonic)))
