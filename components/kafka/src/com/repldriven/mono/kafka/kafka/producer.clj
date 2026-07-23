(ns com.repldriven.mono.kafka.kafka.producer
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.kafka.kafka.config :as config]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [try-nom]]
    [com.repldriven.mono.log.interface :as log])
  (:import
    (java.util.concurrent TimeUnit)
    (org.apache.kafka.clients.producer KafkaProducer ProducerRecord)))

;; Values go on the wire as bytes and are serialised here, by the avro brick,
;; rather than by a Kafka serializer. That keeps schema handling in one place —
;; the same schemas command-schema and event-schema already ship — and keeps a
;; schema registry out of the deployment.
(def ^:private byte-array-serializer
  "org.apache.kafka.common.serialization.ByteArraySerializer")

(def ^:private defaults
  {"key.serializer" byte-array-serializer
   "value.serializer" byte-array-serializer})

(defn create
  [{:keys [conf schemas schema] :as opts}]
  (log/info "Creating Kafka producer:" (:name opts))
  (try-nom :kafka/producer-create
           "Failed to create Kafka producer"
           {:instance (KafkaProducer. (config/->properties (merge defaults
                                                                  conf)))
            :topic (get conf :topic (get conf "topic"))
            :schema (when schema (get schemas (name schema)))}))

(defn- serialize
  [{:keys [schema]} data]
  (if schema (avro/serialize schema data) data))

(defn send
  "Send `data` to the producer's topic. Returns the RecordMetadata, or an
  anomaly. Synchronous: the future is dereferenced so a broker-side failure
  is reported here rather than swallowed."
  ([producer data] (send producer data nil))
  ([producer data {:keys [key timeout-ms] :or {timeout-ms 30000}}]
   (log/debugf "kafka.producer: [topic=%s, data=%s]" (:topic producer) data)
   (error/let-nom>
     [payload (serialize producer data)]
     (try-nom :kafka/producer-send
              "Failed to send message to Kafka"
              (let [{:keys [^KafkaProducer instance topic]} producer
                    record (ProducerRecord. ^String topic
                                            (when key
                                              (.getBytes ^String key
                                                         "UTF-8"))
                                            ^bytes payload)]
                (.get (.send instance record)
                      timeout-ms
                      TimeUnit/MILLISECONDS))))))

(defn send-async
  "Send without waiting for acknowledgement. Returns the java Future, or an
  anomaly if serialisation failed."
  ([producer data] (send-async producer data nil))
  ([producer data {:keys [key]}]
   (error/let-nom>
     [payload (serialize producer data)]
     (try-nom :kafka/producer-send
              "Failed to send message to Kafka"
              (let [{:keys [^KafkaProducer instance topic]} producer
                    record (ProducerRecord. ^String topic
                                            (when key
                                              (.getBytes ^String key
                                                         "UTF-8"))
                                            ^bytes payload)]
                (.send instance record))))))

(defn close
  [producer]
  (try-nom :kafka/producer-close
           "Failed to close Kafka producer"
           (when-let [^KafkaProducer instance (:instance producer)]
             (.close instance))))
