(ns com.repldriven.mono.testcontainers.system.components.mqtt
  (:require
    [com.repldriven.mono.testcontainers.container :as container]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:import
    (java.time Duration)
    (org.testcontainers.hivemq HiveMQContainer)
    (org.testcontainers.utility DockerImageName)))

(def default-exposed-port 1883)
(def default-docker-image-name "hivemq/hivemq-ce:2025.5")

(def container
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [docker-image-name exposed-port]} config]
                         (log/info "Starting mqtt container")
                         (-> (DockerImageName/parse docker-image-name)
                             (.asCompatibleSubstituteFor "hivemq/hivemq-ce")
                             (HiveMQContainer.)
                             (doto (.withStartupTimeout (Duration/ofSeconds
                                                         60)))
                             (container/start! [exposed-port])))))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping mqtt container")
                  (container/stop! instance))
   :system/config {:docker-image-name default-docker-image-name
                   :exposed-port default-exposed-port}
   :system/config-schema [:map [:docker-image-name string?]
                          [:exposed-port int?]]
   :system/instance-schema map?})

(def container-connection-uri
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [container-mapped-ports exposed-port]} config
               connection-uri-str (str "tcp://localhost:"
                                       (get container-mapped-ports
                                            exposed-port))]
           (log/info "Mapped mqtt container-connection-uri:" connection-uri-str)
           connection-uri-str)))
   :system/config {:container-mapped-ports system/required-component
                   :exposed-port default-exposed-port}
   :system/config-schema [:map [:container-mapped-ports map?]
                          [:exposed-port int?]]
   :system/instance-schema string?})
