(ns com.repldriven.mono.server.shed
  (:require
    [com.repldriven.mono.telemetry.interface :as telemetry]

    [clojure.string :as str])
  (:import
    (java.util.concurrent Semaphore)))

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

(defn wrap-max-in-flight
  ([handler n] (wrap-max-in-flight handler n nil))
  ([handler n counter]
   (let [permits (Semaphore. (int n))]
     (fn [request]
       (cond
        (health? request)
        (handler request)

        (.tryAcquire permits)
        (try (handler request) (finally (.release permits)))

        :else
        (do (telemetry/inc-counter! counter
                                    {"http.request.method" (method request)
                                     "error.type" "server/overloaded"})
            overloaded))))))

(defn rejected-counter
  [otel]
  (telemetry/counter {:name rejected-counter-name
                      :description
                      "Requests turned away before any route handled them"
                      :unit "{request}"
                      :otel otel}))
