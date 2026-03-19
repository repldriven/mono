(ns com.repldriven.mono.json.interface
  (:require
    [com.repldriven.mono.json.core :as core]))

(defn read-str
  "Parse a JSON string into Clojure data structures.
  Returns the parsed data or an anomaly on error."
  [s & {:as options}]
  (apply core/read-str s (apply concat options)))

(defn write-str
  "Convert Clojure data structures to a JSON string.
  Returns the JSON string or an anomaly on error."
  [x & {:as options}]
  (apply core/write-str x (apply concat options)))
