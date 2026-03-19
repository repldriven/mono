(ns com.repldriven.mono.server.core
  (:require
    [com.repldriven.mono.server.jetty :as jetty]
    [com.repldriven.mono.server.openapi :as openapi]
    [com.repldriven.mono.server.router :as router]))

(def standard-router-data router/standard-router-data)
(def standard-executor router/standard-executor)
(def default-exception-handlers router/default-exception-handlers)

(defn router-data
  ([] (router/router-data))
  ([exception-handlers] (router/router-data exception-handlers)))

(defn standard-openapi-handler [] (openapi/standard-handler))

(defn standard-openapi-ui-handler [] (openapi/standard-ui-handler))

(defn http-local-url
  "Get the local HTTP URL from a Jetty Server instance."
  [server]
  (jetty/http-local-url server))
