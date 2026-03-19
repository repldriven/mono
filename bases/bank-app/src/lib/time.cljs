(ns lib.time)

(def ^:private minute 60)
(def ^:private hour 3600)
(def ^:private day 86400)
(def ^:private week 604800)
(def ^:private month 2592000)
(def ^:private year 31536000)

(defn time-ago
  [timestamp]
  (if-not timestamp
    ""
    (let [seconds (js/Math.floor
                   (/ (- (js/Date.now) (.getTime (js/Date. timestamp))) 1000))]
      (cond (< seconds minute)
            "just now"
            (< seconds hour)
            (str (js/Math.floor (/ seconds minute)) "m ago")
            (< seconds day)
            (str (js/Math.floor (/ seconds hour)) "h ago")
            (< seconds week)
            (str (js/Math.floor (/ seconds day)) "d ago")
            (< seconds month)
            (str (js/Math.floor (/ seconds week)) "w ago")
            (< seconds year)
            (str (js/Math.floor (/ seconds month)) "mo ago")
            :else
            (str (js/Math.floor (/ seconds year)) "y ago")))))
