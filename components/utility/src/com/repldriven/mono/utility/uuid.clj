(ns com.repldriven.mono.utility.uuid
  "UUID utilities with support for UUIDv7 (sortable, timestamp-based)."
  (:import
    (com.github.f4b6a3.uuid UuidCreator)))

(defn v7
  "Generate a UUIDv7 (timestamp-ordered, RFC 9562).

  UUIDv7 provides:
  - Sortable by creation time
  - 48-bit timestamp prefix (millisecond precision)
  - Better database index performance than UUIDv4
  - Standard UUID format

  Returns a java.util.UUID instance."
  []
  (UuidCreator/getTimeOrderedEpoch))
