(ns com.repldriven.mono.concurrency-limit.options
  (:require
    [com.repldriven.mono.error.interface :as error]))

(def ^:private default-initial 8)

(def defaults
  {:min 1 :max 1000 :headroom 1.5 :windows 10 :window-size 10 :window-ms 1000})

(def schema
  [:or
   pos-int?
   [:map
    {:closed true}
    [:initial {:optional true} pos-int?]
    [:min {:optional true} pos-int?]
    [:max {:optional true} pos-int?]
    [:headroom {:optional true} [:and number? [:>= 1]]]
    [:windows {:optional true} pos-int?]
    [:window-size {:optional true} pos-int?]
    [:window-ms {:optional true} nat-int?]]])

(def ^:private checks
  {:initial [pos-int? "a positive integer"]
   :min [pos-int? "a positive integer"]
   :max [pos-int? "a positive integer"]
   :headroom [(fn [v] (and (number? v) (>= v 1))) "a number of at least 1"]
   :windows [pos-int? "a positive integer"]
   :window-size [pos-int? "a positive integer"]
   :window-ms [nat-int? "a whole number of milliseconds"]})

(defn- invalid
  [k v message]
  (error/reject :concurrency-limit/invalid-options
                {:message message :key k :value v}))

(defn- check-keys
  [opts]
  (some (fn [[k v]]
          (let [[valid? expected] (get checks k)]
            (cond
             (nil? valid?)
             (invalid k v (str "max-in-flight has no option " k))

             (not (valid? v))
             (invalid k v (str "max-in-flight " k " must be " expected)))))
        opts))

(defn- with-initial
  [opts]
  (let [{lo :min hi :max} opts]
    (cond-> opts
            (nil? (:initial opts))
            (assoc :initial
                   (-> default-initial
                       (max lo)
                       (min hi))))))

(defn- check-order
  [opts]
  (let [{:keys [initial] lo :min hi :max} opts]
    (cond
     (> lo initial)
     (invalid :min lo "max-in-flight :min must not exceed :initial")

     (> initial hi)
     (invalid :initial initial "max-in-flight :initial must not exceed :max")

     :else
     opts)))

(defn normalise
  [opts]
  (cond
   (pos-int? opts)
   (assoc defaults :initial opts :min opts :max opts)

   (map? opts)
   (or (check-keys opts) (check-order (with-initial (merge defaults opts))))

   :else
   (invalid nil opts "max-in-flight must be a positive integer or a map")))
