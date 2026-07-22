(ns com.repldriven.mono.spec.core
  (:require
    [malli.core :as m]
    [malli.error :as me]))

(def non-empty-string? [:string {:min 1}])

(defn validate
  [schema data]
  (m/validate schema data))

(defn explain
  [schema data]
  (m/explain schema data))

(defn humanize
  [explanation]
  (me/humanize explanation))
