(ns com.repldriven.mono.json.core
  (:require
    [com.repldriven.mono.error.interface :as error]
    [clojure.data.json :as json]))

(defn read-str
  "Parse a JSON string into Clojure data structures.
  Returns the parsed data or an anomaly on error."
  [s & {:as options}]
  (error/try-nom :json/parse
                 "Failed to parse JSON string"
                 (apply json/read-str s (apply concat options))))

(defn write-str
  "Convert Clojure data structures to a JSON string.
  Returns the JSON string or an anomaly on error."
  [x & {:as options}]
  (error/try-nom :json/serialize
                 "Failed to serialize to JSON string"
                 (apply json/write-str x (apply concat options))))
