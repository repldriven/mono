(ns com.repldriven.mono.fdb.keyspace
  (:import
    (com.apple.foundationdb.record.provider.foundationdb.keyspace
     DirectoryLayerDirectory
     KeySpace)))

(defn path
  "Returns the KeySpacePath for the given name."
  [name]
  (-> (KeySpace. (into-array DirectoryLayerDirectory
                             [(DirectoryLayerDirectory. name)]))
      (.path name name)))
