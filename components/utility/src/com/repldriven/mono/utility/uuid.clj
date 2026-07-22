(ns com.repldriven.mono.utility.uuid
  (:import
    (com.github.f4b6a3.uuid UuidCreator)))

(defn v7 [] (UuidCreator/getTimeOrderedEpoch))
