(ns com.repldriven.mono.testcontainers.system.core
  (:require
    [com.repldriven.mono.testcontainers.system.components.fdb :as fdb]
    [com.repldriven.mono.testcontainers.system.components.mqtt :as mqtt]
    [com.repldriven.mono.testcontainers.system.components.postgres :as postgres]
    [com.repldriven.mono.testcontainers.system.components.pulsar :as pulsar]
    [com.repldriven.mono.testcontainers.system.components.testcontainers :as
     testcontainers]
    [com.repldriven.mono.testcontainers.system.components.vault :as vault]
    [com.repldriven.mono.system.interface :as system]))

;; Generic testcontainer components
(system/defcomponents :testcontainers
                      {:container testcontainers/container
                       :container-mapped-ports testcontainers/mapped-ports
                       :container-mapped-exposed-port
                       testcontainers/mapped-exposed-port
                       :container-uri testcontainers/uri})

;; Vault testcontainer components
(system/defcomponents :vault
                      {:container vault/container
                       :container-api-port testcontainers/mapped-exposed-port
                       :container-api-url testcontainers/uri})

;; MQTT testcontainer components
(system/defcomponents :mqtt
                      {:container mqtt/container
                       :container-mapped-ports testcontainers/mapped-ports
                       :container-connection-uri mqtt/container-connection-uri})

;; Pulsar testcontainer components
(system/defcomponents
 :pulsar
 {:container pulsar/container
  :container-service-port testcontainers/mapped-exposed-port
  :container-service-url testcontainers/uri
  :container-service-http-port testcontainers/mapped-exposed-port
  :container-service-http-url testcontainers/uri})

;; PostgreSQL testcontainer components
(system/defcomponents :postgres
                      {:container postgres/container
                       :container-mapped-exposed-port
                       testcontainers/mapped-exposed-port})

;; FoundationDB testcontainer components
(system/defcomponents :fdb {:container fdb/container})
