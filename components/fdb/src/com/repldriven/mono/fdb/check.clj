(ns com.repldriven.mono.fdb.check
  (:require
    com.repldriven.mono.fdb.record

    [com.repldriven.mono.error.interface :as error])
  (:import
    (com.apple.foundationdb.record RecordIndexUniquenessViolation)
    (com.repldriven.mono.fdb.record Txn)))

(defn txn?
  "True if x is a Txn."
  [x]
  (instance? Txn x))

(defn uniqueness-violation?
  "Returns true if anomaly was caused by a
  RecordIndexUniquenessViolation."
  [anomaly]
  (when (error/anomaly? anomaly)
    (loop [ex (:exception (error/payload anomaly))]
      (cond
       (nil? ex)
       false

       (instance? RecordIndexUniquenessViolation ex)
       true

       :else
       (recur (.getCause ex))))))
