(ns com.repldriven.mono.pulsar.pulsar.client
  (:require
    [com.repldriven.mono.error.interface :refer [try-nom-ex]]
    [com.repldriven.mono.log.interface :as log]
    [clojure.java.data.builder :as builder])
  (:import
    (org.apache.pulsar.client.api ClientBuilder
                                  PulsarClient
                                  PulsarClientException)))

(defn create
  ^PulsarClient [{:keys [service-url]}]
  (log/info "Creating Pulsar client:" service-url)
  (try-nom-ex :pulsar/client-create PulsarClientException
              "Failed to create Pulsar client"
              (builder/to-java PulsarClient
                               (PulsarClient/builder)
                               {:serviceUrl service-url}
                               {:builder-class ClientBuilder})))

(defn close
  [^PulsarClient client]
  (log/info "Closing Pulsar client connection")
  (try-nom-ex :pulsar/client-close PulsarClientException
              "Failed to close Pulsar client connection" (.close client)))
