(ns com.repldriven.mono.fdb.scan
  (:import
    (com.apple.foundationdb.record EndpointType
                                   ExecuteProperties
                                   ScanProperties
                                   TupleRange)
    (com.apple.foundationdb.record.provider.foundationdb
     FDBStoreTimer$Waits)
    (com.apple.foundationdb.tuple Tuple)))

(defn- record->bytes
  [r]
  (-> r
      .getRecord
      .toByteArray))

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
  `{:records [bytes ...] :before cursor|nil :after cursor|nil}`,
  where `:records` is in the requested display order.

  `:before` is the cursor of the first record in the page (what the
  client should send back as `:before` to page *previous*). `:after`
  is the cursor of the last record — only set when more rows exist
  beyond the page — for the client to send back as `:after` to page
  *next*. Both are phrased in the client's display direction, so
  `page[after]` / `page[before]` always mean next / prev regardless
  of whether the natural order is ascending or descending.

  opts:
    :prefix  vector of leading PK parts to scope the scan
    :after   cursor, client's \"next page\" boundary
    :before  cursor, client's \"previous page\" boundary
    :limit   int, page size
    :order   `:asc` (default) or `:desc` — selects the display
             direction; in `:desc` the first page (no cursor)
             returns the highest-keyed records first

  When `:prefix` is given, the scan is constrained to records whose
  PK starts with those values. Cursors are the PK element at the
  position after the prefix."
  [store {:keys [prefix after before limit order]}]
  (let [descending? (= :desc order)
        ;; Translate client-oriented cursors into native range bounds.
        ;; In asc, `:after X` is a low exclusive bound (forward from X+ε);
        ;; `:before X` is a high exclusive bound (reverse to X-ε). In
        ;; desc, the roles swap — "next after X" now means "keys less
        ;; than X", and "prev before X" means "keys greater than X".
        low-cursor (if descending? before after)
        high-cursor (if descending? after before)
        ;; Scan backward when the natural traversal opposes key order:
        ;; asc + `:before` (paginating back from a higher cursor), or
        ;; desc without a low-cursor (default desc scan runs from the
        ;; end down).
        reverse-scan? (if descending?
                        (nil? low-cursor)
                        (some? high-cursor))
        prefix-size (count (or prefix []))
        prefix-tuple (when (seq prefix)
                       (Tuple/from (into-array Object prefix)))
        base-range (when prefix-tuple
                     (prefix-range prefix-tuple))
        range (cond
               (and prefix-tuple low-cursor)
               (TupleRange.
                (cursor-tuple prefix low-cursor)
                (.getHigh ^TupleRange base-range)
                EndpointType/RANGE_EXCLUSIVE
                (.getHighEndpoint ^TupleRange
                                  base-range))

               (and prefix-tuple high-cursor)
               (TupleRange.
                (.getLow ^TupleRange base-range)
                (cursor-tuple prefix high-cursor)
                (.getLowEndpoint ^TupleRange
                                 base-range)
                EndpointType/RANGE_EXCLUSIVE)

               prefix-tuple
               base-range

               low-cursor
               (TupleRange.
                (Tuple/from (into-array Object [low-cursor]))
                nil
                EndpointType/RANGE_EXCLUSIVE
                EndpointType/TREE_END)

               high-cursor
               (TupleRange.
                nil
                (Tuple/from (into-array Object [high-cursor]))
                EndpointType/TREE_START
                EndpointType/RANGE_EXCLUSIVE)

               :else
               TupleRange/ALL)
        execute-props (-> (ExecuteProperties/newBuilder)
                          (.setReturnedRowLimit (inc limit))
                          .build)
        scan-props (ScanProperties. execute-props reverse-scan?)
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
        trimmed (cond-> raw more? (subvec 0 limit))
        ;; Native scan produces records low-to-high on forward and
        ;; high-to-low on reverse. Flip only when the scan direction
        ;; disagrees with the display direction.
        page (if (= reverse-scan? descending?)
               trimmed
               (vec (rseq trimmed)))]
    {:records (mapv record->bytes page)
     :before (when (seq page)
               (cursor (first page) prefix-size))
     :after (when more?
              (cursor (peek page) prefix-size))}))
