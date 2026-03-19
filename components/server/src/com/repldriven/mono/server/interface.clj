(ns com.repldriven.mono.server.interface
  (:require
    com.repldriven.mono.server.system
    [com.repldriven.mono.server.core :as core]))

(def standard-router-data core/standard-router-data)
(def standard-executor core/standard-executor)
(def default-exception-handlers core/default-exception-handlers)

(defn router-data
  ([] (core/router-data))
  ([exception-handlers] (core/router-data exception-handlers)))

(defn standard-openapi-handler [] (core/standard-openapi-handler))

(defn standard-openapi-ui-handler [] (core/standard-openapi-ui-handler))

(defn http-local-url
  "Get the local HTTP URL from a Jetty Server instance."
  [server]
  (core/http-local-url server))
