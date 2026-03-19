(ns com.repldriven.mono.env.interface
  (:require
    [com.repldriven.mono.env.core :as core]))

(def edn-reader core/edn-reader)
(def yml-reader core/yml-reader)

(defn config
  ([] (core/config))
  ([source] (core/config source))
  ([source profile] (core/config source profile)))

