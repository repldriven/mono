(ns com.repldriven.mono.server.system
  (:require
    [com.repldriven.mono.server.jetty :as server-jetty]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]
    [ring.adapter.jetty9 :as jetty])
  (:import
    (java.nio ByteBuffer)
    (java.nio.charset StandardCharsets)
    (org.eclipse.jetty.http HttpFields$Mutable HttpHeader)
    (org.eclipse.jetty.server Request Response Server)
    (org.eclipse.jetty.server.handler ErrorHandler)
    (org.eclipse.jetty.util Callback)))

(defn- assoc-if-missing
  [m ks v]
  (if (or (contains? m ks) (nil? v)) m (assoc-in m ks v)))

(defn- interceptor
  [k v]
  {:name :mono :enter (fn [ctx] (assoc-if-missing ctx [:request k] v))})

(def interceptors
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (do (log/info "Building interceptors:" (keys config))
                           (reduce-kv (fn [coll k v]
                                        (conj coll (interceptor k v)))
                                      []
                                      config))))
   :system/config nil
   :system/instance-schema vector?})

(defn- json-error-body
  [status detail type]
  (str "{\"title\":\"ERROR\""
       ",\"type\":"
       (pr-str type)
       ",\"status\":"
       status
       ",\"detail\":"
       (pr-str detail)
       "}"))

(defn- utf8-bytes
  [^String s]
  (.getBytes s StandardCharsets/UTF_8))

(defn- json-error-handler
  []
  (proxy [ErrorHandler] []
    (handle [^Request request ^Response response ^Callback callback]
      (let [status (.getStatus response)
            message (or (.getAttribute request
                                       ErrorHandler/ERROR_MESSAGE)
                        "Unknown error")
            body (json-error-body status message "server/error")]
        (.put ^HttpFields$Mutable (.getHeaders response)
              HttpHeader/CONTENT_TYPE
              "application/json")
        (.write response
                true
                (ByteBuffer/wrap (utf8-bytes body))
                callback)
        true))
    (badMessageError [status ^String reason ^HttpFields$Mutable fields]
      (.put fields HttpHeader/CONTENT_TYPE "application/json")
      (ByteBuffer/wrap
       (utf8-bytes
        (json-error-body status
                         (or reason "Bad request")
                         "server/bad-message"))))))

(def default-jetty-adapter-options {:join? false :port 0})

(def jetty-adapter
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [handler interceptors ready-fn options]} config
               options (assoc options
                              :configurator
                              (fn [^Server server]
                                (.setErrorHandler server (json-error-handler))))
               ready-thunk (cond (fn? ready-fn)
                                 ready-fn
                                 (instance? clojure.lang.IDeref ready-fn)
                                 (fn [] @ready-fn)
                                 :else
                                 (constantly true))
               ctx {:interceptors interceptors :ready-fn ready-thunk}
               _ (log/info "Starting jetty adapter")
               server (jetty/run-jetty (handler ctx) options)]
           (log/info "Jetty listening on" (server-jetty/http-local-url server))
           server)))
   :system/stop (fn [{:system/keys [^Server instance]}]
                  (when (some? instance) (.stop instance)))
   :system/config {:handler system/required-component
                   :interceptors nil
                   :ready-fn nil
                   :options default-jetty-adapter-options}
   :system/config-schema [:map [:handler fn?]]
   :system/instance-schema some?})

(def http-url
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (server-jetty/http-local-url (:jetty-adapter config))))
   :system/config {:jetty-adapter system/required-component}
   :system/instance-schema string?})

(system/defcomponents
 :server
 {:interceptors interceptors :jetty-adapter jetty-adapter :http-url http-url})
