(ns com.repldriven.mono.kafka.system.components
  (:require
    [com.repldriven.mono.kafka.kafka.consumer :as consumer]
    [com.repldriven.mono.kafka.kafka.producer :as producer]
    [com.repldriven.mono.kafka.kafka.topics :as topics]
    [com.repldriven.mono.kafka.message-bus :as message-bus]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]))

;; ---
;; container
;; ---

;; Reading the bootstrap servers off a *started* container belongs here rather
;; than in testcontainers: a component group that interrogates a running
;; container lives with the component that understands it.
(def bootstrap-servers
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [servers (.getBootstrapServers
                                      (get-in config [:container :container]))]
                         (log/info "Kafka bootstrap servers:" servers)
                         servers)))
   :system/config {:container system/required-component}
   :system/config-schema [:map [:container map?]]
   :system/instance-schema string?})

;; ---
;; admin
;; ---

(def admin
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (topics/create-admin config)))
   :system/stop (fn [{:system/keys [instance]}] (topics/close-admin instance))
   :system/config {:bootstrap-servers system/required-component}
   :system/config-schema [:map [:bootstrap-servers string?]]
   :system/instance-schema some?})

(def topics-component
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [admin topics]} config
                             result (topics/create admin topics)]
                         (when (error/anomaly? result)
                           ;; nosemgrep: no-raw-throw
                           (throw (ex-info "Failed to create Kafka topics"
                                           {:anomaly result})))
                         result)))
   :system/config {:admin system/required-component
                   :topics system/required-component}
   :system/instance-schema some?})

;; ---
;; producer
;; ---

(def producer-component
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (producer/create config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (when instance (producer/close instance)))
   :system/config {:conf system/required-component :schemas nil :schema nil}
   :system/instance-schema some?})

(def producers
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (into {}
               (map (fn [[k v]] [k (producer/create (assoc v :name (name k)))])
                    config))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (dorun (map (fn [[k v]]
                                  (log/info "Closing Kafka producer:" (name k))
                                  (producer/close v))
                                instance))))
   :system/config system/required-component
   :system/instance-schema map?})

;; ---
;; consumer
;; ---

(def consumer-component
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (consumer/create config)))
   :system/config {:conf system/required-component
                   :topics system/required-component
                   :schemas nil
                   :schema nil
                   ;; A producer on the dead-letter topic. Without one, a
                   ;; message that exhausts its redeliveries is dropped.
                   :dead-letter-producer nil
                   :max-redeliveries nil}
   :system/instance-schema some?})

(def consumers
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (into {}
               (map (fn [[k v]] [k (consumer/create (assoc v :name (name k)))])
                    config))))
   :system/config system/required-component
   :system/instance-schema map?})

;; ---
;; message-bus
;; ---

(def message-bus-producers
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (into {}
                             (map (fn [[k {:keys [producer]}]]
                                    [k (message-bus/->KafkaProducer producer)])
                                  config))))
   :system/config system/required-component
   :system/instance-schema map?})

(defn- consumer-or-throw
  "Validates that `consumer` is a started consumer rather than an unresolved
  ref (a vector at runtime) or an anomaly from `create`. Without this the
  failure surfaces later as a tight log loop inside the polling thread, with
  nothing naming the channel that caused it."
  [channel-key consumer]
  (when (or (nil? consumer)
            (vector? consumer)
            (error/anomaly? consumer)
            (not (:instance consumer)))
    ;; nosemgrep: no-raw-throw
    (throw (ex-info (str "Kafka consumer for channel "
                         channel-key
                         " did not resolve to a consumer — got "
                         (type consumer))
                    {:channel channel-key :value consumer})))
  consumer)

(def message-bus-consumers
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (into {}
               (map (fn [[k {:keys [consumer timeout] :or {timeout 1000}}]]
                      [k
                       (message-bus/->KafkaConsumer (consumer-or-throw k
                                                                       consumer)
                                                    timeout
                                                    (atom nil))])
                    config))))
   :system/config system/required-component
   :system/instance-schema map?})
