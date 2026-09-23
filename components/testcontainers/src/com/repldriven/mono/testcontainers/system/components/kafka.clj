(ns com.repldriven.mono.testcontainers.system.components.kafka
  "Kafka testcontainer, using the apache/kafka image in KRaft mode — no
  ZooKeeper container to run alongside it.

  Only the container's lifecycle lives here. Reading the bootstrap servers off
  a started container is the kafka brick's job, in its own system namespace."
  (:require
    [com.repldriven.mono.testcontainers.container :as container]

    [com.repldriven.mono.log.interface :as log])
  (:import
    (com.github.dockerjava.api.command CreateContainerCmd)
    (java.time Duration)
    (java.util.function Consumer)
    (org.testcontainers.images.builder Transferable)
    (org.testcontainers.kafka KafkaContainer)
    (org.testcontainers.utility DockerImageName)))

(def default-docker-image-name "apache/kafka:3.9.1")

;; The broker listens on 9092 inside the container. It has to be listed
;; explicitly because container/start! calls .withExposedPorts, which replaces
;; the container's own list rather than adding to it.
(def default-exposed-port 9092)
(def default-exposed-ports [default-exposed-port])

;; Never reused, whatever `TESTCONTAINERS_REUSE_ENABLE` says: a rig's
;; topics and consumer groups carry fixed names, and test namespaces run
;; in parallel, so two rigs on one broker would be one consumer group
;; taking each other's commands. Reuse waits on per-boot topic names.
;; The library's container writes its start script into the running
;; container and its entrypoint execs the script as soon as the file
;; exists — before the copy has closed it, on a busy host, which fails
;; the start with `Text file busy` (testcontainers-java issue 11682).
;; Until the fix lands, the entrypoint waits instead for a sentinel
;; copied in after the script, as that fix does.
(def ^:private starter-script "/tmp/testcontainers_start.sh")

(def ^:private starter-ready "/tmp/testcontainers_start.ready")

(defn- kafka-container
  [^DockerImageName image]
  ;; Both arities: the lifecycle calls the two-argument one, whose
  ;; default delegates to the one the library's container overrides,
  ;; and a proxied method is bypassed while its own super runs.
  (doto (proxy [KafkaContainer] [image]
          (containerIsStarting
            ([info]
             (let [^KafkaContainer this this]
               (proxy-super containerIsStarting info)
               (.copyFileToContainer this (Transferable/of "") starter-ready)))
            ([info reused]
             (let [^KafkaContainer this this]
               (proxy-super containerIsStarting info reused)
               (.copyFileToContainer this
                                     (Transferable/of "")
                                     starter-ready)))))
    ;; At create time, entrypoint and command together: `withCommand`
    ;; on its own leaves the image's entrypoint in place of the `sh`
    ;; the library's definition sets, and the loop never runs.
    (.withCreateContainerCmdModifier
     (reify
      Consumer
        (accept [_ cmd]
          (.withEntrypoint ^CreateContainerCmd cmd (into-array String ["sh"]))
          (.withCmd ^CreateContainerCmd cmd
                    (into-array String
                                ["-c"
                                 (str "while [ ! -f " starter-ready
                                      " ]; do sleep 0.1; done; "
                                      starter-script)])))))))

(defn- start-container
  [config]
  (let [{:keys [docker-image-name exposed-ports]} config]
    (log/info "Starting kafka container")
    (-> (DockerImageName/parse docker-image-name)
        (.asCompatibleSubstituteFor "apache/kafka")
        (kafka-container)
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
