(ns com.repldriven.mono.testcontainers.system.components.postgres
  (:require
    [com.repldriven.mono.testcontainers.container :as container]
    [com.repldriven.mono.log.interface :as log])
  (:import
    (java.time Duration)
    (org.testcontainers.containers PostgreSQLContainer)
    (org.testcontainers.utility DockerImageName)))

(def default-exposed-port 5432)
(def default-docker-image-name "postgres:16.2")

(defn- start-container
  [config]
  (let [{:keys [docker-image-name exposed-port]} config]
    (log/info "Starting postgres container")
    (-> (DockerImageName/parse docker-image-name)
        (PostgreSQLContainer.)
        (doto (.withStartupTimeout (Duration/ofSeconds 60))
              (.withCommand (into-array String
                                        ["postgres" "-c" "shared_buffers=64MB"
                                         "-c" "work_mem=4MB" "-c"
                                         "maintenance_work_mem=32MB" "-c"
                                         "max_connections=20"])))
        (container/start! [exposed-port]))))

(def container
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (start-container config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping postgres container")
                  (container/stop! instance))
   :system/config {:docker-image-name default-docker-image-name
                   :exposed-port default-exposed-port}
   :system/config-schema [:map [:docker-image-name string?]
                          [:exposed-port int?]]
   :system/instance-schema map?})
