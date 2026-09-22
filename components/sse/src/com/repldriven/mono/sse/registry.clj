(ns com.repldriven.mono.sse.registry
  (:import
    (java.util.concurrent LinkedBlockingQueue TimeUnit)))

(def closed ::closed)

(defn registry [] {:subscribers (atom {}) :open (atom true)})

(defn subscribe!
  [registry topic]
  (let [queue (LinkedBlockingQueue.)]
    (swap! (:subscribers registry) update topic (fnil conj #{}) queue)
    (when-not @(:open registry) (.offer queue closed))
    {:topic topic :queue queue}))

(defn unsubscribe!
  [registry {:keys [topic queue]}]
  (swap! (:subscribers registry)
    (fn [subscribers]
      (let [left (disj (get subscribers topic #{}) queue)]
        (if (empty? left)
          (dissoc subscribers topic)
          (assoc subscribers topic left))))))

(defn publish!
  [registry topic event]
  (doseq [^LinkedBlockingQueue queue (get @(:subscribers registry) topic)]
    (.offer queue event)))

(defn open-count
  [registry topic]
  (count (get @(:subscribers registry) topic)))

(defn next!
  [{:keys [^LinkedBlockingQueue queue]} timeout-ms]
  (.poll queue timeout-ms TimeUnit/MILLISECONDS))

(defn close!
  [registry]
  (reset! (:open registry) false)
  (doseq [^LinkedBlockingQueue queue (apply concat
                                            (vals @(:subscribers registry)))]
    (.offer queue closed)))
