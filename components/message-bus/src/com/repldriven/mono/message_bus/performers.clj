(ns com.repldriven.mono.message-bus.performers
  (:require
    [com.repldriven.mono.log.interface :as log]

    [clojure.core.async :as async]))

(def default-queue 16)

(defn- performer-key
  [key-fn {:keys [data key]}]
  (or (when key-fn
        (try (key-fn data)
             (catch Throwable t
               (log/error t "Performer key fn threw; using the send key")
               nil)))
      key))

(defn- choose
  [n key-fn turn delivery]
  (if-some [k (performer-key key-fn delivery)]
    (mod (hash k) n)
    (mod (vswap! turn inc) n)))

(defn- run-one
  [handler-fn {:keys [data ack nack]}]
  (try (handler-fn data)
       (ack)
       (catch Throwable t (nack t))))

(defn- performer
  [handler-fn queue]
  (async/io-thread (loop []
                     (when-some [delivery (async/<!! queue)]
                       (run-one handler-fn delivery)
                       (recur)))))

(defn perform
  [source handler-fn {:keys [performers key-fn queue]}]
  (let [n (max 1 (or performers 1))
        queues (vec (repeatedly n
                                (fn []
                                  (async/chan (or queue default-queue)))))
        done (mapv (fn [q] (performer handler-fn q)) queues)
        turn (volatile! -1)]
    (async/io-thread (loop []
                       (when-some [delivery (async/<!! source)]
                         (async/>!! (nth queues (choose n key-fn turn delivery))
                                    delivery)
                         (recur)))
                     (run! async/close! queues))
    (async/merge done)))
