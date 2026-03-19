(ns com.repldriven.mono.testcontainers.system.components.testcontainers
  (:require
    [com.repldriven.mono.testcontainers.container :as container]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:import
    (java.time Duration)
    (org.testcontainers.containers GenericContainer)))

(def default-uri-scheme "http")
(def default-uri-host "localhost")
(def default-uri-path "")

(def container
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [docker-image-name exposed-ports startup-timeout]} config]
           (log/info "Starting" docker-image-name "container")
           (-> (GenericContainer. ^String docker-image-name)
               (doto (.withStartupTimeout (Duration/ofSeconds startup-timeout)))
               (container/start! exposed-ports)))))
   :system/stop (fn [{:system/keys [config instance]}]
                  (log/info "Stopping" (:docker-image-name config) "container")
                  (container/stop! instance))
   :system/config {:docker-image-name system/required-component
                   :exposed-ports system/required-component
                   :startup-timeout 60}
   :system/config-schema [:map [:docker-image-name string?]
                          [:exposed-ports [:vector int?]]
                          [:startup-timeout int?]]
   :system/instance-schema map?})

(def mapped-ports
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [container]} config
                             ports (:mapped-ports container)]
                         (log/info "Container mapped ports:" ports)
                         ports)))
   :system/config {:container system/required-component}
   :system/config-schema [:map [:container map?]]
   :system/instance-schema map?})

(def mapped-exposed-port
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [exposed-port container]} config
                             port (get-in container
                                          [:mapped-ports exposed-port])]
                         (log/info "Container mapped exposed port:" port)
                         port)))
   :system/config {:container system/required-component
                   :exposed-port system/required-component}
   :system/config-schema [:map [:container map?] [:exposed-port int?]]
   :system/instance-schema int?})

(def uri
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [scheme host port path]} config
                             uri-str (str scheme "://" host ":" port path)]
                         (log/info "Mapped container uri:" uri-str)
                         uri-str)))
   :system/config {:scheme default-uri-scheme
                   :host default-uri-host
                   :port system/required-component
                   :path default-uri-path}
   :system/config-schema [:map [:scheme string?] [:host string?] [:port int?]
                          [:path string?]]
   :system/instance-schema string?})
