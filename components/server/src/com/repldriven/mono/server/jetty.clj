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

(defn exclusive-ephemeral-ports!
  "Turn reuse-address off on every connector configured with port 0, so
  the bind refuses any port another process already holds.

  With reuse-address on, the kernel's free-port search for a wildcard
  bind matches only sockets on the same address, so an ephemeral port
  can land on one that another process holds on loopback, and loopback
  connections then reach that process. A connector on a fixed port
  keeps Jetty's default, which lets a restart reclaim the port from
  TIME_WAIT."
  [^Server server]
  (doseq [connector (.getConnectors server)
          :when (and (instance? ServerConnector connector)
                     (zero? (.getPort ^ServerConnector connector)))]
    (.setReuseAddress ^ServerConnector connector false)))
