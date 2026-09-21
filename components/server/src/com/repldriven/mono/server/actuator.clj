(ns com.repldriven.mono.server.actuator)

(def ^:private json {"content-type" "application/json"})

(defn- status-body
  [up?]
  {:status (if up? 200 503) :headers json :body {:status (if up? "UP" "DOWN")}})

(defn health-routes
  [ctx]
  (let [ready-fn (or (:ready-fn ctx) (constantly true))
        liveness (fn [_] (status-body true))
        readiness (fn [_] (status-body (boolean (ready-fn))))
        aggregate (fn [_]
                    (let [ready? (boolean (ready-fn))]
                      (assoc-in (status-body ready?)
                       [:body :components]
                       {:liveness {:status "UP"}
                        :readiness {:status
                                    (if ready? "UP" "DOWN")}})))]
    [["/actuator/health" {:get {:no-doc true :handler aggregate}}]
     ["/actuator/health/liveness" {:get {:no-doc true :handler liveness}}]
     ["/actuator/health/readiness" {:get {:no-doc true :handler readiness}}]]))
