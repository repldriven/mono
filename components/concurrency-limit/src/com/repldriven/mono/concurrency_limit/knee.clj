(ns com.repldriven.mono.concurrency-limit.knee)

(def ^:private search-gain 2.0)

(def ^:private search-growth 1.25)

(def ^:private cycle-length 8)

(def ^:private drain-phase 0)

(def ^:private probe-phase (dec cycle-length))

(def ^:private drain-gain 0.75)

(def ^:private probe-gain 1.25)

(defn init
  [opts]
  {:limit (double (:initial opts)) :mode :search :phase nil :history []})

(defn capacity
  [history]
  (some->> history
           (filter :bound?)
           seq
           (map :throughput)
           (apply max)))

(defn floor
  [history]
  (some->> history
           (keep :latency)
           seq
           (apply min)))

(defn- hold-limit
  [phase knee headroom]
  (cond
   (= drain-phase phase)
   (* drain-gain knee)

   (= probe-phase phase)
   (* probe-gain headroom knee)

   :else
   (* headroom knee)))

(defn- search
  [state window before]
  (let [{:keys [throughput bound?]} window]
    (cond
     (not bound?)
     state

     (or (nil? before) (>= throughput (* search-growth before)))
     (update state :limit * search-gain)

     :else
     (assoc state :mode :hold :phase nil))))

(defn- hold
  [state knee headroom]
  (let [{:keys [phase]} state
        phase (if (nil? phase) drain-phase (mod (inc phase) cycle-length))]
    (assoc state :phase phase :limit (hold-limit phase knee headroom))))

(defn- clamp
  [state opts]
  (let [{lo :min hi :max} opts]
    (update state
            :limit
            (fn [limit]
              (-> limit
                  (max (double lo))
                  (min (double hi)))))))

(defn step
  [state window opts]
  (let [{:keys [windows headroom]} opts
        before (capacity (:history state))
        history (vec (take-last windows (conj (:history state) window)))
        state (assoc state :history history)
        cap (capacity history)
        flr (floor history)]
    (clamp (cond
            (nil? cap)
            (-> state
                (assoc :mode :search)
                (update :limit max (double (:initial opts))))

            (nil? flr)
            (assoc state :mode :search)

            (= :search (:mode state))
            (let [state (search state window before)]
              (cond-> state
                      (= :hold (:mode state))
                      (hold (* cap flr) headroom)))

            :else
            (hold state (* cap flr) headroom))
           opts)))
