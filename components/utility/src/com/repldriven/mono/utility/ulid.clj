(ns com.repldriven.mono.utility.ulid
  (:import
    (com.github.f4b6a3.ulid UlidCreator)))

(defn monotonic
  []
  (-> (UlidCreator/getMonotonicUlid)
      .toString
      .toLowerCase))
