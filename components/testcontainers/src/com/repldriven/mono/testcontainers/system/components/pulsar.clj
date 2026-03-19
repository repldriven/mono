(ns com.repldriven.mono.testcontainers.system.components.pulsar
  (:require
    [com.repldriven.mono.testcontainers.container :as container]
    [com.repldriven.mono.log.interface :as log])
  (:import
    (java.time Duration)
    (org.testcontainers.containers PulsarContainer)
    (org.testcontainers.utility DockerImageName)))

(def default-exposed-broker-port 6650)
(def default-exposed-broker-http-port 8080)

(def default-exposed-ports
  [default-exposed-broker-port default-exposed-broker-http-port])

(def default-docker-image-name "apachepulsar/pulsar:4.1.2")

(defn- start-container
  [config]
  (let [{:keys [docker-image-name exposed-ports]} config]
    (log/info "Starting pulsar container")
    (-> (DockerImageName/parse docker-image-name)
        (.asCompatibleSubstituteFor "apachepulsar/pulsar")
        (PulsarContainer.)
        (doto (.addEnv "PULSAR_MEM"
                       (str "-Xms128m -Xmx128m"
                            " -XX:MaxDirectMemorySize=128m"))
              (.addEnv "PULSAR_GC" "-XX:+UseSerialGC")
              (.withStartupTimeout (Duration/ofMinutes 3)))
        (container/start! exposed-ports))))

(def container
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (start-container config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping pulsar container")
                  (container/stop! instance))
   :system/config {:docker-image-name default-docker-image-name
                   :exposed-ports default-exposed-ports}
   :system/config-schema [:map [:docker-image-name string?]
                          [:exposed-ports [:vector int?]]]
   :system/instance-schema map?})
