(ns com.repldriven.mono.server.system
  (:require
    [com.repldriven.mono.server.jetty :as server-jetty]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]
    [ring.adapter.jetty9 :as jetty])
  (:import
    (org.eclipse.jetty.server Server)))

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

(def default-jetty-adapter-options {:join? false :port 0})

(def jetty-adapter
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [handler interceptors options]} config
               _ (log/info "Starting jetty adapter")
               server (jetty/run-jetty (handler {:interceptors interceptors})
                                       options)]
           (log/info "Jetty listening on" (server-jetty/http-local-url server))
           server)))
   :system/stop (fn [{:system/keys [^Server instance]}]
                  (when (some? instance) (.stop instance)))
   :system/config {:handler system/required-component
                   :interceptors nil
                   :options default-jetty-adapter-options}
   :system/config-schema [:map [:handler fn?]]
   :system/instance-schema some?})

(system/defcomponents :server
                      {:interceptors interceptors :jetty-adapter jetty-adapter})
