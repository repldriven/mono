(ns com.repldriven.mono.sse.stream
  (:require
    [com.repldriven.mono.sse.registry :as registry]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(def default-keep-alive-ms 15000)

(defn- send-event
  "Emit `event` and, where `emit` took it, tell `on-sent`. Answers the
  anomaly `emit` returned, or nil."
  [emit on-sent event]
  (let [result (emit event)]
    (if (error/anomaly? result)
      result
      (do (when on-sent (on-sent event)) nil))))

(defn- send-pending
  "Emit what was pending, answering the ids sent or the first anomaly."
  [emit on-sent events]
  (reduce (fn [sent event]
            (if-let [failed (send-event emit on-sent event)]
              (reduced failed)
              (conj sent (:id event))))
          #{}
          events))

(defn serve
  [registry topic emit opts]
  (let [{:keys [keep-alive-ms pending on-sent last-event-id]
         :or {keep-alive-ms default-keep-alive-ms}}
        opts
        subscription (registry/subscribe! registry topic)]
    (try
      (let-nom> [_ (emit nil)
                 events (if pending (pending last-event-id) [])
                 sent (send-pending emit on-sent events)]
        (loop [sent sent]
          (let [event (registry/next! subscription keep-alive-ms)]
            (cond (= registry/closed event)
                  nil

                  (nil? event)
                  (let [result (emit nil)]
                    (if (error/anomaly? result) result (recur sent)))

                  (contains? sent (:id event))
                  (recur sent)

                  :else
                  (or (send-event emit on-sent event)
                      (recur (conj sent (:id event))))))))
      (finally (registry/unsubscribe! registry subscription)))))
