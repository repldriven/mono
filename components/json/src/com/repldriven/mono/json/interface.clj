(ns com.repldriven.mono.json.interface
  "JSON read/write helpers wrapping `clojure.data.json`. Parse and
  serialize errors come back as `:json/parse` or `:json/serialize`
  anomalies."
  (:require
    [com.repldriven.mono.json.core :as core]))

(defn read-str
  "Parse a JSON string into Clojure data, or return an anomaly.

  Args:
  - s: the JSON string.
  - options: optional `clojure.data.json` kwargs."
  [s & {:as options}]
  (apply core/read-str s (apply concat options)))

(defn write-str
  "Serialize Clojure data to a JSON string, or return an anomaly.

  Args:
  - x: the value to serialize.
  - options: optional `clojure.data.json` kwargs."
  [x & {:as options}]
  (apply core/write-str x (apply concat options)))
