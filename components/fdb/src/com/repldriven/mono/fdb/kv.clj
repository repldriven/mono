(ns com.repldriven.mono.fdb.kv
  (:require
    [com.repldriven.mono.error.interface :refer [try-nom]])
  (:import
    (com.apple.foundationdb Database)
    (java.util.function Function)))

(defn set-str
  "Set a string key-value pair in the FDB database."
  [^Database db ^String key ^String value]
  (try-nom
   :fdb/set-str
   {:message "Failed to set value" :key key}
   (.run db
         ^Function (fn [tr] (.set tr (.getBytes key) (.getBytes value)) nil))))

(defn get-str
  "Get a string value by key from the FDB database.
  Returns nil if the key does not exist."
  [^Database db ^String key]
  (try-nom :fdb/get-str
           {:message "Failed to get value" :key key}
           (.run db
                 ^Function
                 (fn [tr]
                   (some-> (.get tr (.getBytes key))
                           .join
                           (String.))))))

(defn set-bytes
  "Set a byte array value for a string key in FDB."
  [^Database db ^String key ^bytes value]
  (try-nom
   :fdb/set-bytes
   {:message "Failed to set bytes" :key key}
   (.run db ^Function (fn [tr] (.set tr (.getBytes key) value) nil))))

(defn get-bytes
  "Get a byte array value by key from FDB.
  Returns nil if the key does not exist."
  [^Database db ^String key]
  (try-nom :fdb/get-bytes
           {:message "Failed to get bytes" :key key}
           (.run db
                 ^Function
                 (fn [tr]
                   (some-> (.get tr (.getBytes key))
                           .join)))))
