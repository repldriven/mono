(ns com.repldriven.mono.transit.core
  (:refer-clojure :exclude [read])
  (:require
    [com.repldriven.mono.error.interface :refer [try-nom]]

    [cognitect.transit :as transit])
  (:import
    (java.io ByteArrayInputStream ByteArrayOutputStream)
    (java.nio.charset StandardCharsets)))

(defn- read-from
  [in options]
  (transit/read (transit/reader in :json options)))

(defn- write-to
  [x out options]
  (transit/write (transit/writer out :json options) x))

(defn read-str
  [^String s & {:as options}]
  (try-nom :transit/parse
           "Failed to parse transit+json string"
           (read-from (ByteArrayInputStream. (.getBytes s
                                                        StandardCharsets/UTF_8))
                      options)))

(defn read
  [in & {:as options}]
  (try-nom :transit/parse
           "Failed to parse transit+json"
           (read-from in options)))

(defn write-str
  [x & {:as options}]
  (try-nom :transit/serialize
           "Failed to serialize to transit+json string"
           (let [out (ByteArrayOutputStream.)]
             (write-to x out options)
             (.toString out StandardCharsets/UTF_8))))

(defn write
  [x out & {:as options}]
  (try-nom :transit/serialize
           "Failed to serialize to transit+json"
           (write-to x out options)))
