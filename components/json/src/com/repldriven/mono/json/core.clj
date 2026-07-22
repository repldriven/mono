(ns com.repldriven.mono.json.core
  (:require
    [com.repldriven.mono.error.interface :refer [try-nom]]
    [clojure.data.json :as json]))

(defn read-str
  [s & {:as options}]
  (try-nom :json/parse
           "Failed to parse JSON string"
           (apply json/read-str s (apply concat options))))

(defn write-str
  [x & {:as options}]
  (try-nom :json/serialize
           "Failed to serialize to JSON string"
           (apply json/write-str x (apply concat options))))
