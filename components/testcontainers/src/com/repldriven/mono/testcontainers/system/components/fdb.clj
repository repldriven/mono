(ns com.repldriven.mono.testcontainers.system.components.fdb
  (:require
    [com.repldriven.mono.log.interface :as log])
  (:import
    (java.io File)
    (java.net ServerSocket)
    (java.time Duration)
    (org.testcontainers.containers FixedHostPortGenericContainer)
    (org.testcontainers.containers.wait.strategy Wait)
    (org.testcontainers.images.builder ImageFromDockerfile)))

(def fdb-version "7.3.75")
(def default-image-name (str "mono/foundationdb:" fdb-version))

(defn- workspace-root
  []
  (loop [dir (File. (System/getProperty "user.dir"))]
    (if (.exists (File. dir "workspace.edn"))
      (.toPath dir)
      (if-let [parent (.getParentFile dir)]
        (recur parent)
        (throw (ex-info
                "Could not find workspace.edn - are we in a Polylith project?"
                {}))))))

(defn- fdb-image
  [image-name]
  (let [root (workspace-root)
        fdb-dir (.resolve root "infra/docker/fdb")]
    (-> (ImageFromDockerfile. image-name false)
        (.withFileFromPath "Dockerfile" (.resolve fdb-dir "Dockerfile"))
        (.withFileFromPath "fdb.bash" (.resolve fdb-dir "fdb.bash")))))

(defn- free-port
  "Finds a free port by briefly opening and closing a server socket."
  []
  (with-open [ss (ServerSocket. 0)] (.getLocalPort ss)))

(defn- start-container
  [config]
  (let [{:keys [image-name]} config
        _ (log/info "Building FDB image:" image-name)
        built-name (.get (fdb-image image-name))
        port (free-port)
        _ (log/info "Starting FDB container, image:" built-name "port:" port)]
    (doto (FixedHostPortGenericContainer. ^String built-name)
      (.withFixedExposedPort (int port) (int port))
      (.addExposedPort (int port))
      (.withEnv "FDB_PORT" (str port))
      (.withStartupTimeout (Duration/ofSeconds 120))
      (.waitingFor (Wait/forListeningPort))
      (.start))))

(def container
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (start-container config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (log/info "Stopping FDB container")
                  (when (some? instance) (.stop instance)))
   :system/config {:image-name default-image-name}
   :system/config-schema [:map [:image-name string?]]
   :system/instance-schema some?})
