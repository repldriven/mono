(ns com.repldriven.mono.testcontainers.system.components.kafka
  "Kafka testcontainer, using the apache/kafka image in KRaft mode — no
  ZooKeeper container to run alongside it.

  Only the container's lifecycle lives here. Reading the bootstrap servers off
  a started container is the kafka brick's job, in its own system namespace."
  (:require
    [com.repldriven.mono.testcontainers.container :as container]

    [com.repldriven.mono.log.interface :as log])
  (:import
    (java.time Duration)
    (org.testcontainers.kafka KafkaContainer)
    (org.testcontainers.utility DockerImageName)))

(def default-docker-image-name "apache/kafka:3.9.1")

;; The broker listens on 9092 inside the container. It has to be listed
;; explicitly because container/start! calls .withExposedPorts, which replaces
;; the container's own list rather than adding to it.
(def default-exposed-port 9092)
(def default-exposed-ports [default-exposed-port])

(defn- start-container
  [config]
  (let [{:keys [docker-image-name exposed-ports]} config]
    (log/info "Starting kafka container")
    (-> (DockerImageName/parse docker-image-name)
        (.asCompatibleSubstituteFor "apache/kafka")
        (KafkaContainer.)
        (doto (.withStartupTimeout (Duration/ofMinutes 2)))
        (container/start! exposed-ports))))

(def container
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (start-container config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping kafka container")
                  (container/stop! instance))
   :system/config {:docker-image-name default-docker-image-name
                   :exposed-ports default-exposed-ports}
   :system/config-schema [:map [:docker-image-name string?]
                          [:exposed-ports [:vector int?]]]
   :system/instance-schema map?})
