(ns com.repldriven.mono.utility.uuid
  (:import
    (com.github.f4b6a3.uuid UuidCreator)))

(defn v7 [] (UuidCreator/getTimeOrderedEpoch))

(defn suffix
  "The last n characters of a v7 UUID's string form. n stays within 12
  so the result comes from the random tail and never from the timestamp
  that leads a v7."
  [n]
  (let [s (str (v7))]
    (subs s (- (count s) n))))
