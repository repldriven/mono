(ns com.repldriven.mono.server.jetty
  (:import
    (org.eclipse.jetty.server Server ServerConnector)))

(defn http-local-url
  "Get the local HTTP URL from a Jetty Server instance.
  Returns a URL string built from the first connector's host and port."
  [^Server server]
  (let [^ServerConnector connector (first (.getConnectors server))
        host (.getHost connector)
        port (.getLocalPort connector)]
    (str "http://" (or host "localhost") ":" port)))
