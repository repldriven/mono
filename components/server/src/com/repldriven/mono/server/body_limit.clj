(ns com.repldriven.mono.server.body-limit
  (:require
    [com.repldriven.mono.utility.interface :as util]

    [clojure.string :as str])
  (:import
    (org.eclipse.jetty.http HttpException HttpStatus)
    (org.eclipse.jetty.server Server)
    (org.eclipse.jetty.server.handler SizeLimitHandler)))

(def ^:private unlimited -1)

(defn configure
  [^Server server max-request-bytes]
  (.insertHandler server
                  (SizeLimitHandler. (long max-request-bytes) unlimited)))

(defn wrap-max-request-bytes
  [handler max-request-bytes]
  (fn [request]
    (handler (assoc request :server/max-request-bytes max-request-bytes))))

(defn too-large?
  [^Throwable e]
  (some (fn [t]
          (and (instance? HttpException t)
               (= HttpStatus/PAYLOAD_TOO_LARGE_413
                  (.getCode ^HttpException t))))
        (take-while some? (iterate ex-cause e))))

(defn problem
  [max-request-bytes]
  (util/assoc-some {:title "REJECTED"
                    :type "server/request-too-large"
                    :status HttpStatus/PAYLOAD_TOO_LARGE_413
                    :detail (if max-request-bytes
                              (str "The request body is larger than "
                                   max-request-bytes
                                   " bytes")
                              "The request body is too large")}
                   :max-request-bytes
                   max-request-bytes))

(defn response
  [request]
  (let [{:server/keys [max-request-bytes]} request]
    {:status HttpStatus/PAYLOAD_TOO_LARGE_413
     :body (problem max-request-bytes)}))

(defn problem-json
  [max-request-bytes]
  (str "{"
       (str/join ","
                 (map
                  (fn [[k v]]
                    (str (pr-str (name k)) ":" (if (string? v) (pr-str v) v)))
                  (problem max-request-bytes)))
       "}"))
