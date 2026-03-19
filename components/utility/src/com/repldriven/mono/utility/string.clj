(ns com.repldriven.mono.utility.string
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
    (java.io ByteArrayInputStream)
    (java.nio.charset StandardCharsets)))

(defn string->stream
  "Convert a string to an InputStream with the specified encoding."
  ([s] (string->stream s "UTF-8"))
  ([^String s ^String encoding]
   (-> s
       (.getBytes encoding)
       (ByteArrayInputStream.))))

(defn str->bytes
  "Convert a string to a UTF-8 byte array."
  [^String s]
  (.getBytes s StandardCharsets/UTF_8))

(defn resolve-source
  "Resolve a source string to a resource. Handles classpath: prefix."
  [source]
  (if (str/starts-with? source "classpath:")
    (io/resource (subs source (count "classpath:")))
    source))

(defn prop-seq->kw-map
  "Convert a sequence of property strings (\"key=value\") to a keyword map."
  [props]
  (into {}
        (map (fn [kv]
               (let [[k v] (mapv str/trim (str/split kv #"="))]
                 [(keyword k) v]))
             props)))
