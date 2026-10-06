(ns com.repldriven.mono.server.shed
  (:require
    [com.repldriven.mono.concurrency-limit.interface :as concurrency-limit]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.telemetry.interface :as telemetry]

    [clojure.string :as str]))

(def ^:private retry-after-s 1)

(def ^:private overloaded
  {:status 503
   :headers {"content-type" "application/json"
             "Retry-After" (str retry-after-s)}
   :body (str "{\"title\":\"ERROR\""
              ",\"type\":\"server/overloaded\""
              ",\"status\":503"
              ",\"detail\":\"The server is handling as many requests as it"
              " takes. Retry after "
              retry-after-s
              " second.\"}")})

(defn- health?
  [request]
  (str/starts-with? (or (:uri request) "") "/actuator/"))

(def rejected-counter-name "mono.server.request.rejected")

(defn- method
  [request]
  (some-> (:request-method request)
          name
          str/upper-case))

(defn wrap-limiter
  [handler limiter counter]
  (fn [request]
    (if (health? request)
      (handler request)
      (if-some [permit (concurrency-limit/try-acquire limiter)]
        (let [returned (volatile! false)]
          (try (let [response (handler request)]
                 (vreset! returned true)
                 response)
               (finally (concurrency-limit/release permit
                                                   (if @returned
                                                     :success
                                                     :ignored)))))
        (do (telemetry/inc-counter! counter
                                    {"http.request.method" (method request)
                                     "error.type" "server/overloaded"})
            overloaded)))))

(defn wrap-max-in-flight
  ([handler max-in-flight] (wrap-max-in-flight handler max-in-flight nil))
  ([handler max-in-flight counter]
   (error/let-nom> [limiter (concurrency-limit/limiter max-in-flight)]
     (wrap-limiter handler limiter counter))))

(defn rejected-counter
  [otel]
  (telemetry/counter {:name rejected-counter-name
                      :description
                      "Requests turned away before any route handled them"
                      :unit "{request}"
                      :otel otel}))

(def ^:private gauges-of
  [["mono.server.request.limit"
    "Requests the server admits at once"
    "{request}"
    concurrency-limit/limit]
   ["mono.server.request.active"
    "Requests the server is handling"
    "{request}"
    concurrency-limit/in-flight]
   ["mono.server.request.capacity"
    "Requests a second the server has measured it serves"
    "{request}/s"
    concurrency-limit/capacity]])

(defn gauges
  [limiter otel]
  (mapv (fn [[gauge-name description unit observe]]
          (telemetry/gauge {:name gauge-name
                            :description description
                            :unit unit
                            :otel otel
                            :observe (fn [] (observe limiter))}))
        gauges-of))

(defn close-gauges [gauges] (run! telemetry/close-instrument gauges))
