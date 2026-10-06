(ns com.repldriven.mono.concurrency-limit.window)

(def ^:private nanos-per-ms 1000000)

(defn open
  [now]
  {:opened now :count 0 :latency 0 :timed 0 :bound? false})

(defn sample
  [window started now]
  (let [{:keys [opened]} window]
    (cond-> (update window :count inc)
            (>= started opened)
            (-> (update :latency + (- now started))
                (update :timed inc)))))

(defn bound [window] (assoc window :bound? true))

(defn full?
  [window now opts]
  (let [{:keys [opened] n :count} window
        {:keys [window-size window-ms]} opts]
    (and (>= n window-size) (>= (- now opened) (* window-ms nanos-per-ms)))))

(defn close
  [window now]
  (let [{:keys [opened latency timed bound?] n :count} window]
    {:throughput (/ (double n) (max 1 (- now opened)))
     :latency (when (pos? timed) (/ (double latency) timed))
     :bound? bound?}))
