(ns com.repldriven.mono.fdb.record
  (:refer-clojure :exclude [load])
  (:require
    [com.repldriven.mono.error.interface :refer [try-nom]])
  (:import
    (com.apple.foundationdb.record EndpointType
                                   ExecuteProperties
                                   ScanProperties
                                   TupleRange)
    (com.apple.foundationdb.record.provider.foundationdb
     FDBDatabase
     FDBStoreTimer$Waits)
    (com.apple.foundationdb.record.query RecordQuery)
    (com.apple.foundationdb.record.query.expressions Query)
    (com.apple.foundationdb.tuple Tuple)
    (com.google.protobuf MessageLite)
    (java.util.function Function)))

(defn open-store
  "Opens a record store by calling the store function (returned by
  the store or meta-store system component) with the given context
  and store name."
  [open-store-fn ctx store-name]
  (open-store-fn ctx store-name))

(defn- record->bytes
  [r]
  (-> r
      .getRecord
      .toByteArray))

(defn load
  "Loads a record by primary key from an open FDBRecordStore.
  Returns serialized bytes or nil. For use inside transact.
  Accepts one or more primary key parts for composite keys."
  [store & primary-key-parts]
  (some-> (.loadRecord store (Tuple/from (into-array Object primary-key-parts)))
          record->bytes))

(defn save
  "Saves a Java protobuf Message to an open FDBRecordStore.
  For use inside transact."
  [store ^MessageLite record]
  (.saveRecord store record)
  nil)

(defn query
  "Queries an open FDBRecordStore where field equals value.
  Returns a vector of serialized byte arrays. For use inside
  transact."
  [store record-type field value]
  (let [q (-> (RecordQuery/newBuilder)
              (.setRecordType record-type)
              (.setFilter (-> (Query/field field)
                              (.equalsValue value)))
              .build)]
    (->> (.executeQuery store q)
         .asList
         (.asyncToSync (.getContext store)
                       FDBStoreTimer$Waits/WAIT_EXECUTE_QUERY)
         (mapv record->bytes))))

(defn transact
  "Opens an FDB Record Layer store and runs f within a single
  transaction. f receives the open FDBRecordStore and should
  return the transaction result.

  The 6-arg form accepts a custom nom category and message for
  call-site-specific anomaly reporting."
  ([^FDBDatabase record-db open-store-fn store-name f]
   (transact record-db
             open-store-fn
             store-name
             f
             :fdb/transact
             "Failed to execute transaction"))
  ([^FDBDatabase record-db open-store-fn store-name f category message]
   (try-nom category
            message
            (.run record-db
                  ^Function
                  (fn [ctx]
                    (f (open-store open-store-fn ctx store-name)))))))

(defn transact-multi
  "Runs f within a single FDB transaction, passing a function
  that opens stores by name. f receives open-store and should
  call (open-store \"store-name\") for each store it needs.
  All writes across stores are atomic."
  ([^FDBDatabase record-db open-store-fn f]
   (transact-multi record-db
                   open-store-fn
                   f
                   :fdb/transact
                   "Failed to execute transaction"))
  ([^FDBDatabase record-db open-store-fn f category message]
   (try-nom category
            message
            (.run record-db
                  ^Function
                  (fn [ctx]
                    (f (fn [store-name]
                         (open-store open-store-fn ctx store-name))))))))

(defn- prefix-range
  "Returns a TupleRange scoped to a prefix tuple."
  [prefix-tuple]
  (TupleRange/allOf prefix-tuple))

(defn- cursor-tuple
  "Builds a cursor Tuple from prefix parts and a cursor
  value."
  [prefix cursor]
  (let [parts (into (vec prefix) [cursor])]
    (Tuple/from (into-array Object parts))))

(defn- cursor
  "Extracts the cursor element from a record's primary
  key at the given position."
  [r position]
  (.get (.getPrimaryKey r) (int position)))

(defn scan
  "Scans records by primary key order. Returns
  {:records [bytes ...] :before cursor|nil :after cursor|nil}.

  :after is the cursor for the next forward page (nil when
  no more records). :before is the first record's cursor
  (nil when empty).

  opts:
    :prefix  vector of leading PK parts to scope the scan
    :after   cursor, exclusive lower bound (forward)
    :before  cursor, exclusive upper bound (reverse)
    :limit   int, page size

  When :prefix is given, the scan is constrained to records
  whose PK starts with those values. Cursors are the PK
  element at the position after the prefix."
  [store {:keys [prefix after before limit]}]
  (let [reverse? (some? before)
        prefix-size (count (or prefix []))
        prefix-tuple (when (seq prefix)
                       (Tuple/from (into-array Object prefix)))
        base-range (when prefix-tuple
                     (prefix-range prefix-tuple))
        range (cond
               (and prefix-tuple after)
               (TupleRange.
                (cursor-tuple prefix after)
                (.getHigh ^TupleRange base-range)
                EndpointType/RANGE_EXCLUSIVE
                (.getHighEndpoint ^TupleRange
                                  base-range))

               (and prefix-tuple before)
               (TupleRange.
                (.getLow ^TupleRange base-range)
                (cursor-tuple prefix before)
                (.getLowEndpoint ^TupleRange
                                 base-range)
                EndpointType/RANGE_EXCLUSIVE)

               prefix-tuple
               base-range

               after
               (TupleRange.
                (Tuple/from (into-array Object [after]))
                nil
                EndpointType/RANGE_EXCLUSIVE
                EndpointType/TREE_END)

               before
               (TupleRange.
                nil
                (Tuple/from (into-array Object [before]))
                EndpointType/TREE_START
                EndpointType/RANGE_EXCLUSIVE)

               :else
               TupleRange/ALL)
        execute-props (-> (ExecuteProperties/newBuilder)
                          (.setReturnedRowLimit (inc limit))
                          .build)
        scan-props (ScanProperties. execute-props reverse?)
        raw (->> (.scanRecords store
                               ^TupleRange range
                               nil
                               ^ScanProperties scan-props)
                 .asList
                 (.asyncToSync
                  (.getContext store)
                  FDBStoreTimer$Waits/WAIT_SCAN_RECORDS)
                 vec)
        more? (> (count raw) limit)
        page (cond->
              raw

              more?
              (subvec 0 limit)

              reverse?
              (-> rseq
                  vec))]
    {:records (mapv record->bytes page)
     :before (when (seq page)
               (cursor (first page) prefix-size))
     :after (when more?
              (cursor (peek page) prefix-size))}))
