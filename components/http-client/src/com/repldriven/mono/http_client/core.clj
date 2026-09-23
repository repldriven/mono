(ns com.repldriven.mono.http-client.core
  (:refer-clojure :exclude [get])
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.telemetry.interface :as telemetry]

    [org.httpkit.client :as client])
  (:import
    (java.net URI)))

;; http-kit reports client-level failures — DNS, connection refused, timeout —
;; as an :error key on an otherwise ordinary response, rather than by throwing.
;; HTTP statuses are NOT that: a 500 is a perfectly good response and stays
;; one.
(defn- ->result
  [res]
  (if-let [err (:error res)]
    (error/fail :http-client/request
                {:message "HTTP request failed" :error err :res res})
    res))

;; The span carries the method, host, port and path, never the query
;; string: a path is what a reader groups requests by, and a query can
;; carry a token.
(defn- span-attributes
  [{:keys [method url]}]
  (let [uri (try (URI. (str url)) (catch Exception _ nil))]
    (cond-> {:http.request.method (.toUpperCase (name (or method :get)))}
            uri
            (assoc :server.address (.getHost uri)
                   :server.port (.getPort uri)
                   :url.path (or (.getPath uri) "")))))

(defn request
  [opts]
  (telemetry/with-span
   {:name "http-request"
    :kind :client
    :attributes (span-attributes opts)}
   (let [res (error/try-nom :http-client/request
                            "HTTP request threw an exception"
                            (->result @(client/request opts)))]
     (when-let [status (:status res)]
       (telemetry/set-attribute :http.response.status_code status))
     res)))

;; The callback's return value is what http-kit delivers to the promise, so
;; passing the conversion as the callback is all it takes for an async caller
;; to receive exactly what a sync one would.
(defn request-async
  [opts]
  (try (client/request opts ->result)
       (catch Exception e
         (doto (promise)
           (deliver (error/fail :http-client/request
                                {:message "HTTP request threw an exception"
                                 :exception e}))))))

(defn- verb
  [method opts url]
  (assoc opts :method method :url url))

(defn get [url opts] (request (verb :get opts url)))
(defn get-async [url opts] (request-async (verb :get opts url)))
(defn post [url opts] (request (verb :post opts url)))
(defn post-async [url opts] (request-async (verb :post opts url)))
(defn put [url opts] (request (verb :put opts url)))
(defn put-async [url opts] (request-async (verb :put opts url)))
(defn patch [url opts] (request (verb :patch opts url)))
(defn patch-async [url opts] (request-async (verb :patch opts url)))
(defn delete [url opts] (request (verb :delete opts url)))
(defn delete-async [url opts] (request-async (verb :delete opts url)))
(defn head [url opts] (request (verb :head opts url)))
(defn head-async [url opts] (request-async (verb :head opts url)))
(defn options [url opts] (request (verb :options opts url)))
(defn options-async [url opts] (request-async (verb :options opts url)))

(defn url-encode [s] (client/url-encode s))

(defn query-string
  ([m] (client/query-string m))
  ([m style] (client/query-string m style)))

(defn make-client
  [opts]
  (error/try-nom :http-client/make-client
                 "Failed to build HTTP client"
                 (client/make-client opts)))
