(ns com.repldriven.mono.fdb.counter
  (:import
    (com.apple.foundationdb MutationType)
    (com.apple.foundationdb.record.provider.foundationdb
     FDBStoreTimer$Waits)
    (com.apple.foundationdb.tuple Tuple)
    (java.nio ByteBuffer ByteOrder)))

(defn- pack [parts] (.pack (Tuple/from (into-array Object parts))))

(defn- long->little-endian
  [n]
  (-> (ByteBuffer/allocate 8)
      (.order ByteOrder/LITTLE_ENDIAN)
      (.putLong n)
      .array))

(defn- little-endian->long
  [bs]
  (-> (ByteBuffer/wrap bs)
      (.order ByteOrder/LITTLE_ENDIAN)
      .getLong))

(defn allocate
  "Atomically increments counter at key-parts by 1,
  returns the post-increment value. For use inside a
  transaction (pass store to extract raw tr)."
  [store & key-parts]
  (let [ctx (.getContext store)
        tr (.ensureActive ctx)
        key (pack key-parts)]
    (.mutate tr MutationType/ADD key (long->little-endian 1))
    (->
      (.asyncToSync ctx FDBStoreTimer$Waits/WAIT_LOAD_SYSTEM_KEY (.get tr key))
      little-endian->long)))
