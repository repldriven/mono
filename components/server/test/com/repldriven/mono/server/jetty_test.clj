(ns com.repldriven.mono.server.jetty-test
  (:require
    [com.repldriven.mono.server.jetty :as jetty]

    [clojure.test :refer [deftest is testing]])
  (:import
    (org.eclipse.jetty.server Connector Server ServerConnector)))

(deftest exclusive-ephemeral-ports-test
  (let [server (Server.)
        ephemeral (doto (ServerConnector. server) (.setPort 0))
        fixed (doto (ServerConnector. server) (.setPort 8080))]
    (.setConnectors server (into-array Connector [ephemeral fixed]))
    (jetty/exclusive-ephemeral-ports! server)
    (testing "a connector on port 0 refuses a port any process holds"
      (is (false? (.getReuseAddress ephemeral))))
    (testing "a connector on a fixed port keeps Jetty's default"
      (is (true? (.getReuseAddress fixed))))))
