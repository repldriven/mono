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

(defmacro ^:private named
  [thread-name & body]
  `(let [thread# (Thread/currentThread)
         was# (.getName thread#)]
     (.setName thread# ~thread-name)
     (try ~@body (finally (.setName thread# was#)))))

(defn- performer
  [handler-fn queue thread-name]
  (async/io-thread (named thread-name
                          (loop []
                            (when-some [delivery (async/<!! queue)]
                              (run-one handler-fn delivery)
                              (recur))))))

(defn perform
  [source handler-fn {:keys [performers key-fn queue name]}]
  (let [n (max 1 (or performers 1))
        prefix (or name "subscription")
        queues (vec (repeatedly n
                                (fn []
                                  (async/chan (or queue default-queue)))))
        done (vec (map-indexed (fn [i q]
                                 (performer handler-fn
                                            q
                                            (str prefix "-performer-" i)))
                               queues))
        turn (volatile! -1)]
    (async/io-thread (named (str prefix "-dispatcher")
                            (loop []
                              (when-some [delivery (async/<!! source)]
                                (async/>!! (nth queues
                                                (choose n key-fn turn delivery))
                                           delivery)
                                (recur))))
                     (run! async/close! queues))
    (async/merge done)))
