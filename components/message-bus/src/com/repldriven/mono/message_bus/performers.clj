(ns com.repldriven.mono.message-bus.performers
  (:require
    [com.repldriven.mono.concurrency-limit.interface :as concurrency-limit]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.telemetry.interface :as telemetry]

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

(defn- permit
  [limiter]
  (when limiter
    (let [permit (concurrency-limit/acquire limiter)]
      (if (error/anomaly? permit)
        (log/warn "Interrupted waiting for a permit; handling without one")
        permit))))

(defn- run-one
  [handler-fn limiter {:keys [data ack nack]}]
  (let [held (permit limiter)
        outcome (try (handler-fn data)
                     (ack)
                     :success
                     (catch Throwable t (nack t) :ignored))]
    (when held (concurrency-limit/release held outcome))))

(defmacro ^:private named
  [thread-name & body]
  `(let [thread# (Thread/currentThread)
         was# (.getName thread#)]
     (.setName thread# ~thread-name)
     (try ~@body (finally (.setName thread# was#)))))

(defn- performer
  [handler-fn limiter queue thread-name]
  (async/io-thread (named thread-name
                          (loop []
                            (when-some [delivery (async/<!! queue)]
                              (run-one handler-fn limiter delivery)
                              (recur))))))

(defn subscription-limiter
  [performers max-in-flight]
  (let [n (max 1 (or performers 1))
        {hi :max} (when (map? max-in-flight) max-in-flight)]
    (cond
     (nil? max-in-flight)
     nil

     (and hi (> hi n))
     (error/reject :message-bus/invalid-max-in-flight
                   {:message (str "max-in-flight :max "
                                  hi
                                  " exceeds the consumer's "
                                  n
                                  " performers")
                    :max hi
                    :performers n})

     (map? max-in-flight)
     (concurrency-limit/limiter (merge {:max n} max-in-flight))

     :else
     (concurrency-limit/limiter max-in-flight))))

(def ^:private gauges-of
  [["mono.message_bus.subscription.limit"
    "Messages the subscription handles at once"
    "{message}"
    concurrency-limit/limit]
   ["mono.message_bus.subscription.active"
    "Messages the subscription is handling"
    "{message}"
    concurrency-limit/in-flight]
   ["mono.message_bus.subscription.capacity"
    "Messages a second the subscription has measured it handles"
    "{message}/s"
    concurrency-limit/capacity]])

(defn- gauges
  [limiter subscription-name]
  (when limiter
    (mapv (fn [[gauge-name description unit observe]]
            (telemetry/gauge {:name gauge-name
                              :description description
                              :unit unit
                              :observe
                              (fn []
                                (when-some [v (observe limiter)]
                                  [{:value v
                                    :attributes {"messaging.consumer.group.name"
                                                 subscription-name}}]))}))
          gauges-of)))

(defn perform
  [source handler-fn {:keys [performers max-in-flight key-fn queue name]}]
  (error/let-nom>
    [limiter (subscription-limiter performers max-in-flight)
     n (max 1 (or performers 1))
     prefix (or name "subscription")
     instruments (gauges limiter prefix)
     queues (vec (repeatedly n (fn [] (async/chan (or queue default-queue)))))
     done (vec (map-indexed (fn [i q]
                              (performer handler-fn
                                         limiter
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
                     (run! async/close! queues)
                     (run! telemetry/close-instrument instruments))
    (async/merge done)))
