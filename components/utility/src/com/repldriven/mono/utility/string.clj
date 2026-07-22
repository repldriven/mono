(ns com.repldriven.mono.utility.string
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
    (java.io ByteArrayInputStream)
    (java.nio.charset StandardCharsets)))

(defn string->stream
  ([s] (string->stream s "UTF-8"))
  ([^String s ^String encoding]
   (-> s
       (.getBytes encoding)
       (ByteArrayInputStream.))))

(defn str->bytes
  [^String s]
  (.getBytes s StandardCharsets/UTF_8))

(defn resolve-source
  [source]
  (if (str/starts-with? source "classpath:")
    (io/resource (subs source (count "classpath:")))
    source))

(defn prop-seq->kw-map
  [props]
  (into {}
        (map (fn [kv]
               (let [[k v] (mapv str/trim (str/split kv #"="))]
                 [(keyword k) v]))
             props)))
