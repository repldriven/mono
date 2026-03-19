(ns com.repldriven.mono.pulsar.system.components
  (:refer-clojure :exclude [name namespace type])
  (:require
    [com.repldriven.mono.pulsar.message-bus :as message-bus]
    [com.repldriven.mono.pulsar.pulsar.admin :as admin]
    [com.repldriven.mono.pulsar.pulsar.client :as client]
    [com.repldriven.mono.pulsar.pulsar.consumer :as consumer]
    [com.repldriven.mono.pulsar.pulsar.crypto :as crypto]
    [com.repldriven.mono.pulsar.pulsar.namespaces :as namespaces]
    [com.repldriven.mono.pulsar.pulsar.producer :as producer]
    [com.repldriven.mono.pulsar.pulsar.reader :as reader]
    [com.repldriven.mono.pulsar.pulsar.schemas :as schemas]
    [com.repldriven.mono.pulsar.pulsar.tenants :as tenants]
    [com.repldriven.mono.pulsar.pulsar.topics :as topics]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.log.interface :as log]))

;; ---
;; container urls
;; ---

(def broker-url
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [url (.getPulsarBrokerUrl
                                  (get-in config [:container :container]))]
                         (log/info "Pulsar broker URL:" url)
                         url)))
   :system/config {:container system/required-component}
   :system/config-schema [:map [:container map?]]
   :system/instance-schema string?})

(def http-service-url
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [url (.getHttpServiceUrl
                                  (get-in config [:container :container]))]
                         (log/info "Pulsar HTTP service URL:" url)
                         url)))
   :system/config {:container system/required-component}
   :system/config-schema [:map [:container map?]]
   :system/instance-schema string?})

;; ---
;; admin
;; ---

(def admin
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (admin/create config)))
   :system/stop (fn [{:system/keys [instance]}] (admin/close instance))
   :system/config {:service-http-url system/required-component}
   :system/config-schema [:map [:service-http-url string?]]
   :system/instance-schema some?})

;; ---
;; client
;; ---

(def client
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (client/create config)))
   :system/stop (fn [{:system/keys [instance]}] (client/close instance))
   :system/config {:service-url system/required-component}
   :system/config-schema [:map [:service-url string?]]
   :system/instance-schema some?})

;; ---
;; consumer
;; ---

(def consumers
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (into {}
               (map (fn [[k v]] [k
                                 (consumer/create
                                  (assoc v :name (clojure.core/name k)))])
                    config))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (dorun (map (fn [[k v]]
                                  (log/info "Closing Pulsar consumer:"
                                            (clojure.core/name k))
                                  (consumer/close v))
                                instance))))
   :system/config system/required-component
   :system/instance-schema map?})

(def consumer
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (consumer/create config)))
   :system/stop (fn [{:system/keys [instance]}] (consumer/close instance))
   :system/config {:client system/required-component
                   :conf system/required-component}
   :system/config-schema [:map [:client some?] [:conf some?]]
   :system/instance-schema some?})

;; ---
;; crypto
;; ---

(def crypto-key-pair-generator
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (crypto/key-pair-generator config)))
   :system/config system/required-component
   :system/instance-schema some?})

;; crypto-key-pair-file-reader(s)
(def crypto-key-pair-file-readers
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (into {}
               (map (fn [[k v]] [k (crypto/key-pair-file-reader v)]) config))))
   :system/config system/required-component
   :system/instance-schema map?})

(def crypto-key-pair-file-reader
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (crypto/key-pair-file-reader config)))
   :system/config system/required-component
   :system/instance-schema some?})

;; crypto-key-reader(s)
(def crypto-key-readers
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (into {} (map (fn [[k v]] [k (crypto/key-reader v)]) config))))
   :system/config system/required-component
   :system/instance-schema map?})

(def crypto-key-reader
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (crypto/key-reader config)))
   :system/config system/required-component
   :system/instance-schema some?})

;; ---
;; namespaces
;; ---

(def namespaces
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (namespaces/create-namespaces config)))
   :system/config system/required-component})

;; ---
;; producer(s)
;; ---

(def producers
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (into {}
               (map (fn [[k v]] [k
                                 (producer/create
                                  (assoc v :name (clojure.core/name k)))])
                    config))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (dorun (map (fn [[k v]]
                                  (log/info "Closing Pulsar producer:"
                                            (clojure.core/name k))
                                  (producer/close v))
                                instance))))
   :system/config system/required-component
   :system/instance-schema map?})

(def producer
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (producer/create config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (when instance (producer/close instance)))
   :system/config {:client system/required-component
                   :conf system/required-component
                   :schemas nil}
   :system/config-schema [:map [:client some?] [:conf some?]]
   :system/instance-schema some?})

;; ---
;; reader
;; ---

(def readers
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (into {}
               (map (fn [[k v]] [k
                                 (reader/create
                                  (assoc v :name (clojure.core/name k)))])
                    config))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (dorun (map (fn [[k v]]
                                  (log/info "Closing Pulsar reader:"
                                            (clojure.core/name k))
                                  (reader/close v))
                                instance))))
   :system/config system/required-component
   :system/instance-schema map?})

(def reader
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (reader/create config)))
   :system/stop (fn [{:system/keys [instance]}] (reader/close instance))
   :system/config {:client system/required-component
                   :conf system/required-component
                   :schemas nil}
   :system/config-schema [:map [:client some?] [:conf some?]]
   :system/instance-schema some?})

;; ---
;; schemas
;; ---

(def schemas
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (schemas/create-schemas config)))
   :system/config system/required-component
   :system/instance-schema some?})

;; ---
;; tenants
;; ---

(def tenants
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (tenants/create-tenants config)))
   :system/config system/required-component})

;; ---
;; topics
;; ---

(def topics
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (topics/create-topics config)))
   :system/config system/required-component})

;; ---
;; message-bus
;; ---

(def message-bus-producers
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (into {}
                             (map (fn [[k {:keys [producer]}]]
                                    [k (message-bus/->PulsarProducer producer)])
                                  config))))
   :system/config system/required-component
   :system/instance-schema map?})

(def message-bus-consumers
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (into {}
                             (map (fn [[k {:keys [consumer timeout]}]]
                                    [k
                                     (message-bus/map->PulsarConsumer
                                      {:consumer consumer
                                       :timeout (or timeout 10000)
                                       :stop-ch (atom nil)})])
                                  config))))
   :system/config system/required-component
   :system/instance-schema map?})
