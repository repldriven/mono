(ns com.repldriven.mono.server.shed
  (:require
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

(defn wrap-max-in-flight
  [handler n]
  (let [permits (Semaphore. (int n))]
    (fn [request]
      (cond
       (health? request)
       (handler request)

       (.tryAcquire permits)
       (try (handler request) (finally (.release permits)))

       :else
       overloaded))))
