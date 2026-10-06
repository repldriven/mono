(ns com.repldriven.mono.concurrency-limit.core
  (:require
    [com.repldriven.mono.concurrency-limit.knee :as knee]
    [com.repldriven.mono.concurrency-limit.options :as options]
    [com.repldriven.mono.concurrency-limit.window :as window]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.utility.interface :as util]))

(def ^:private nanos-per-second 1e9)

(defn- permits
  ^long [state]
  (let [{:keys [estimator]} state]
    (long (Math/floor (double (:limit estimator))))))

(defn- admit
  [state now]
  (let [state (update state :window (fn [w] (or w (window/open now))))]
    (if (< (:in-flight state) (permits state))
      (update state :in-flight inc)
      (update state :window window/bound))))

(defn- settle
  [state started now opts]
  (let [state (update state :in-flight dec)
        w (cond-> (or (:window state) (window/open now))
                  started
                  (window/sample started now))]
    (if (window/full? w now opts)
      (-> state
          (assoc :window (when (pos? (:in-flight state)) (window/open now)))
          (update :estimator knee/step (window/close w now) opts))
      (assoc state :window w))))

(defn limiter
  [opts]
  (error/let-nom> [opts (options/normalise opts)]
    {:opts opts
     :monitor (Object.)
     :state (atom {:in-flight 0 :window nil :estimator (knee/init opts)})}))

(defn try-acquire
  [limiter]
  (let [[before after] (swap-vals! (:state limiter) admit (util/nanos))]
    (when (> (:in-flight after) (:in-flight before))
      {:limiter limiter :started (util/nanos)})))

(defn acquire
  [limiter]
  (let [^Object monitor (:monitor limiter)
        result (error/try-nom-ex :concurrency-limit/acquire
                                 InterruptedException
                                 "interrupted while waiting for a permit"
                                 (locking monitor
                                   (loop []
                                     (or (try-acquire limiter)
                                         (do (.wait monitor) (recur))))))]
    (when (error/anomaly? result) (.interrupt (Thread/currentThread)))
    result))

(defn release
  ([permit] (release permit :success))
  ([permit outcome]
   (let [{:keys [limiter started]} permit
         {:keys [opts state]} limiter
         ^Object monitor (:monitor limiter)
         now (util/nanos)]
     (swap! state settle (when (= :success outcome) started) now opts)
     (locking monitor (.notifyAll monitor))
     nil)))

(defn limit [limiter] (permits @(:state limiter)))

(defn in-flight [limiter] (:in-flight @(:state limiter)))

(defn capacity
  [limiter]
  (let [{:keys [estimator]} @(:state limiter)]
    (some-> (:history estimator)
            knee/capacity
            (* nanos-per-second))))
