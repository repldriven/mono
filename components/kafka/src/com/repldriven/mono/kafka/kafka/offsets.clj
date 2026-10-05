(ns com.repldriven.mono.kafka.kafka.offsets
  "Which offset a partition can commit when its messages finish out of
  order.

  Kafka commits a position, not a message: committing offset 108 says
  everything before it is done. So the position committed is the lowest
  offset handed out and not yet finished, or one past the highest
  finished when none is outstanding. A count per offset tolerates a
  message handed out twice, which a seek back for redelivery does to
  every message after it. An offset waiting to be redelivered stays
  outstanding until it is handed out again, so nothing commits past it
  in between. A partition lost in a rebalance is dropped, so it neither
  holds back nor receives a commit.

  The state is a map of `[topic partition]` to
  `{:out (sorted-map offset count) :done highest :committed position
  :waiting #{offset}}`, owned by the polling thread.")

(defn handed-out
  [state tp offset]
  (if (contains? (get-in state [tp :waiting]) offset)
    (update-in state [tp :waiting] disj offset)
    (update-in state
               [tp :out]
               (fn [out] (update (or out (sorted-map)) offset (fnil inc 0))))))

(defn finished
  [state tp offset]
  (if-let [n (get-in state [tp :out offset])]
    (-> state
        (update-in [tp :out]
                   (fn [out]
                     (if (> n 1)
                       (assoc out offset (dec n))
                       (dissoc out offset))))
        (update-in [tp :done] (fnil max offset) offset))
    state))

(defn waiting
  [state tp offset]
  (if (get-in state [tp :out offset])
    (update-in state [tp :waiting] (fnil conj #{}) offset)
    state))

(defn earliest-waiting
  [state tp]
  (some->> (get-in state [tp :waiting])
           seq
           (apply min)))

(defn position
  [state tp]
  (let [{:keys [out done]} (get state tp)]
    (if (seq out)
      (ffirst out)
      (some-> done
              inc))))

(defn to-commit
  [state]
  (into {}
        (keep (fn [[tp {:keys [committed]}]]
                (let [p (position state tp)]
                  (when (and p (or (nil? committed) (> p committed)))
                    [tp p]))))
        state))

(defn committed
  [state positions]
  (reduce-kv (fn [s tp p] (assoc-in s [tp :committed] p)) state positions))

(defn retain
  [state assigned]
  (select-keys state assigned))
