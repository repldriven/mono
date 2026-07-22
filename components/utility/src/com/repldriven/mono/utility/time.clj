(ns com.repldriven.mono.utility.time
  (:import
    (java.time Instant LocalDate ZoneOffset)))

(defn now ^long [] (System/currentTimeMillis))

(defn now-rfc3339 ^String [] (str (Instant/now)))

(defn today
  "Current UTC calendar day as an epoch-day (long), derived from
  `now` so it tracks the same clock."
  ^long []
  (.toEpochDay (LocalDate/ofInstant (Instant/ofEpochMilli (now))
                                    ZoneOffset/UTC)))

(defn epoch-day->iso-date
  "An epoch-day (long) as an ISO-8601 calendar date string
  (`2026-06-18`). Inverse of the epoch-day produced by `today`."
  ^String [^long epoch-day]
  (str (LocalDate/ofEpochDay epoch-day)))
