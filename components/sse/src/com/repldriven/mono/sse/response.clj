(ns com.repldriven.mono.sse.response
  (:require
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.server.interface :as server]

    [clojure.string :as str])
  (:import
    (java.io IOException OutputStream)
    (java.nio.charset StandardCharsets)))

(def ^:private keep-alive ": keep-alive\n\n")

(def ^:private headers
  {"content-type" "text/event-stream; charset=utf-8"
   "cache-control" "no-cache"
   "x-accel-buffering" "no"})

(defn frame
  [message]
  (if message
    (let [{:keys [event id data retry]} message]
      (str (when event (str "event: " event "\n"))
           (when id (str "id: " id "\n"))
           (when retry (str "retry: " retry "\n"))
           (str/join (map (fn [line] (str "data: " line "\n"))
                          (str/split-lines (or data ""))))
           "\n"))
    keep-alive))

(defn- message
  [event-name event]
  (when event
    (let-nom> [data (json/write-str event)]
      (cond-> {:data data}
              event-name
              (assoc :event event-name)

              (:id event)
              (assoc :id (:id event))))))

(defn- write!
  [^OutputStream out s]
  (error/try-nom-ex :sse/closed
                    IOException
                    "the client closed the stream"
                    (.write out (.getBytes ^String s StandardCharsets/UTF_8))
                    (.flush out)
                    nil))

(defn write-events
  [run opts ^OutputStream out]
  (let [{:keys [event]} opts
        result (run (fn [e]
                      (let-nom> [m (message event e)]
                        (write! out (frame m)))))]
    (cond (not (error/anomaly? result))
          nil

          (= :sse/closed (error/kind result))
          (log/debug "event stream closed by the client")

          :else
          (log/error "event stream ended:" (error/format-anomaly result)))))

(defn response
  [run opts]
  {:status 200
   :headers headers
   :body (server/streaming-body (fn [out] (write-events run opts out)))})
