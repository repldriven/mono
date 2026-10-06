(ns com.repldriven.mono.concurrency-limit.knee-test
  (:require
    [com.repldriven.mono.concurrency-limit.knee :as SUT]

    [com.repldriven.mono.concurrency-limit.options :as options]

    [clojure.test :refer [deftest is testing]]))

(def ^:private nanos-per-second 1e9)

(defn- window-at
  [store limit load]
  (let [{:keys [concurrent latency-ms]} store
        permits (long (Math/floor limit))
        unloaded (/ latency-ms 1000.0)
        capacity (/ concurrent unloaded)]
    (cond
     (> load capacity)
     {:throughput (/ (min permits concurrent) unloaded)
      :latency (* unloaded (max 1.0 (/ permits (double concurrent))))
      :bound? true}

     (> (* load unloaded) permits)
     {:throughput (/ permits unloaded) :latency unloaded :bound? true}

     :else
     {:throughput (double load) :latency unloaded :bound? false})))

(defn- per-nano
  [window]
  (-> window
      (update :throughput / nanos-per-second)
      (update :latency * nanos-per-second)))

(defn- run
  [opts steps]
  (let [opts (options/normalise opts)]
    (reduce (fn [runs [store load]]
              (let [{:keys [limit]} (:state (peek runs))
                    window (window-at store limit load)]
                (conj runs
                      {:limit limit
                       :window window
                       :state (SUT/step (:state (peek runs))
                                        (per-nano window)
                                        opts)})))
            [{:state (SUT/init opts)}]
            steps)))

(defn- limits [runs] (map :limit (rest runs)))

(defn- close-to? [a b] (< (abs (- a b)) 1e-6))

(defn- settled-in?
  [expected runs n]
  (every? (fn [limit] (some (fn [e] (close-to? e limit)) expected))
          (take-last n (limits runs))))

(def ^:private larger {:concurrent 300 :latency-ms 50})

(def ^:private smaller {:concurrent 120 :latency-ms 50})

(defn- overload [store load n] (repeat n [store load]))

(deftest search-test
  (testing "the limit doubles while throughput rises, then drains and holds"
    (let [runs (run {:initial 200} (overload larger 8000 5))]
      (is (every? true? (map close-to? [200 400 800 225 450] (limits runs))))))
  (testing "a store saturated below the start drains to its knee"
    (let [runs (run {:initial 200} (overload smaller 8000 40))]
      (is (settled-in? [90 180 225] runs 16)))))

(deftest hold-test
  (testing "the limit holds at headroom times the knee, with drain and probe"
    (let [runs (run {:initial 200} (overload larger 8000 40))]
      (is (settled-in? [225 450 562.5] runs 16))))
  (testing "throughput stays within a few percent of capacity"
    (let [runs (run {:initial 200} (overload larger 8000 40))
          throughputs (map (fn [r] (get-in r [:window :throughput]))
                           (take-last 32 (rest runs)))]
      (is (<= (* 0.95 6000) (/ (reduce + throughputs) (count throughputs))))))
  (testing "headroom moves the limit and leaves throughput as it is"
    (is (settled-in? [225 300 375]
                     (run {:initial 200 :headroom 1} (overload larger 8000 40))
                     16))
    (is (settled-in? [225 600 750]
                     (run {:initial 200 :headroom 2} (overload larger 8000 40))
                     16))))

(deftest store-changes-test
  (testing "when every request slows, the knee and the limit stay"
    (let [slower (assoc larger :latency-ms 100)
          runs (run {:initial 200}
                    (concat (overload larger 8000 40)
                            (overload slower 8000 40)))]
      (is (settled-in? [225 450 562.5] runs 16))))
  (testing "capacity raised mid-run is found and held"
    (let [wider (assoc larger :concurrent 400)
          runs (run {:initial 200}
                    (concat (overload larger 8000 40)
                            (overload wider 10000 40)))]
      (is (settled-in? [300 600 750] runs 16)))))

(deftest ramp-test
  (testing "a ramp is turned away below capacity only as it crosses the limit"
    (let [steps (concat (map (fn [i] [larger (* 8000.0 (/ i 60))]) (range 60))
                        (overload larger 8000 20))
          runs (run {:initial 200} steps)
          shed-below-capacity (filter (fn [[[_ load] r]]
                                        (and (< load 6000)
                                             (get-in r [:window :bound?])))
                                      (map vector steps (rest runs)))]
      (is (<= (count shed-below-capacity) 1))
      (is (settled-in? [225 450 562.5] runs 8)))))

(deftest quiet-test
  (testing "quiet traffic moves nothing, and a burst searches again"
    (let [runs (run {:initial 200}
                    (concat (repeat 20 [larger 1000])
                            (overload larger 8000 2)))]
      (is (every? (fn [limit] (close-to? 200 limit)) (take 21 (limits runs))))
      (is (close-to? 400 (last (limits runs))))))
  (testing "a search after a quiet spell starts from initial where lower"
    (let [runs (run {:initial 400}
                    (concat (overload smaller 8000 40)
                            (repeat 10 [smaller 1000])
                            (overload smaller 8000 2)))
          [held restarted doubled] (take-last 3 (limits runs))]
      (is (< held 400))
      (is (close-to? 400 restarted))
      (is (close-to? 800 doubled))))
  (testing "a search after a quiet spell starts from the limit where higher"
    (let [runs (run {:initial 200}
                    (concat (overload larger 8000 40)
                            (repeat 10 [larger 1000])
                            (overload larger 8000 1)))]
      (is (< 200 (last (limits runs)))))))

(deftest bounds-test
  (testing "the limit never leaves min and max"
    (let [runs (run {:initial 200 :max 500} (overload larger 8000 40))]
      (is (every? (fn [limit] (<= 1 limit 500)) (limits runs)))
      (is (settled-in? [225 450 500] runs 16))))
  (testing "a limit clamped to min or max stays a double"
    (is (every? double?
                (limits (run
                         {:initial 8 :max 16}
                         (overload {:concurrent 4 :latency-ms 20} 1000 40)))))
    (is (every? double?
                (limits (run
                         {:initial 4 :min 4}
                         (overload {:concurrent 1 :latency-ms 20} 1000 40))))))
  (testing "an integer never moves"
    (is (every? (fn [limit] (close-to? 200 limit))
                (limits (run 200 (overload larger 8000 40)))))))

(deftest consumer-test
  (testing "sixteen performers settle at the knee of a four-writer store"
    (let [runs (run {:max 16}
                    (overload {:concurrent 4 :latency-ms 20} 1000 40))]
      (is (settled-in? [3 6 7.5] runs 16)))))
