(ns com.repldriven.mono.realworld-store.sql
  "Fragments and conversions shared by the queries."
  (:import
    (java.sql Array)))

(def timestamp
  "Timestamps are formatted in SQL rather than in Clojure.

  Two reasons. It removes any question of whether pgjdbc hands back a
  `java.sql.Timestamp` or an `OffsetDateTime`, and microsecond precision
  guarantees an update's `updatedAt` differs from its `createdAt` — which
  the suite asserts directly, and which millisecond precision would make a
  genuine if occasional flake."
  "to_char(%s at time zone 'utc', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')")

(defn at
  [column]
  (format timestamp column))

(defn ->vec
  "A postgres array column as a Clojure vector.

  pgjdbc returns `java.sql.Array` rather than anything Clojure-shaped, and
  an empty `array_agg` is null rather than an empty array."
  [v]
  (cond (nil? v)
        []

        (instance? Array v)
        (vec (.getArray ^Array v))

        (coll? v)
        (vec v)

        :else
        []))
