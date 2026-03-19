(ns com.repldriven.mono.pulsar.pulsar.producer
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.pulsar.pulsar.generic-record :as
     generic-record]
    [com.repldriven.mono.pulsar.pulsar.schemas :as schemas]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [clojure.data.json :as json]
    [clojure.java.data :as j])
  (:import
    (java.util Map)
    (org.apache.pulsar.client.api Producer
                                  ProducerBuilder
                                  PulsarClient
                                  PulsarClientException
                                  Schema)
    (org.apache.pulsar.common.schema SchemaType)))

(defn- add-encryption-keys
  [^ProducerBuilder producer ks]
  (reduce #(.addEncryptionKey ^ProducerBuilder %1 %2) producer ks))

(defn- pulsar-schema->avro-schema
  "Extract the Avro schema from a Pulsar Schema if it's an AVRO type."
  [^Schema pulsar-schema]
  (when (and pulsar-schema
             (= SchemaType/AVRO (.. pulsar-schema getSchemaInfo getType)))
    (-> pulsar-schema
        .getSchemaInfo
        .toString
        json/read-str
        (get "schema")
        json/write-str
        org.apache.pulsar.shade.org.apache.avro.Schema/parse)))

(defn- serialize
  "Serialize data to Avro GenericRecord if schema is present, otherwise return as-is."
  [producer data]
  (let [{:keys [avro-schema]} producer]
    (if avro-schema (generic-record/serialize avro-schema data) data)))

(defn create
  [{:keys [^PulsarClient client conf schemas] :as opts}]
  (log/info "Creating Pulsar producer:" (:name opts))
  (error/try-nom-ex
   :pulsar/producer-create PulsarClientException
   "Failed to create Pulsar producer"
   (let [{:keys [cryptoKeyReader encryptionKeys schema]} conf
         manual-conf [:cryptoKeyReader :encryptionKeys :schema]
         conf-str-keys (into {} (map (fn [[k v]] [(name k) v]) conf))
         auto-conf (j/to-java
                    Map
                    (apply dissoc conf-str-keys (map name manual-conf)))
         resolved-schema (when (some? schema)
                           (schemas/resolve schemas schema))
         instance (if resolved-schema
                    (.. client (newProducer resolved-schema))
                    (.. client newProducer))
         ^ProducerBuilder builder (.. instance (loadConf auto-conf))
         ^ProducerBuilder builder-with-conf
         (cond-> builder
                 (some? cryptoKeyReader)
                 (.cryptoKeyReader cryptoKeyReader)
                 (some? encryptionKeys)
                 (add-encryption-keys encryptionKeys))
         avro-schema (pulsar-schema->avro-schema resolved-schema)]
     {:instance (.create builder-with-conf)
      :schema resolved-schema
      :avro-schema avro-schema})))

(defn send
  ([producer data]
   (log/debugf "pulsar.pulsar.producer: [producer=%s, data=%s]" producer data)
   (error/try-nom-ex :pulsar/producer-send PulsarClientException
                     "Failed to send message to Pulsar"
                     (let [{:keys [^Producer instance]} producer]
                       (.send instance (serialize producer data)))))
  ([producer data opts]
   (log/debugf "pulsar.pulsar.producer: [producer=%s, data=%s, opts=%s]"
               producer
               data
               opts)
   (error/try-nom-ex :pulsar/producer-send PulsarClientException
                     "Failed to send message to Pulsar"
                     (let [{:keys [^Producer instance]} producer]
                       (.. instance
                           newMessage
                           (loadConf opts)
                           (value (serialize producer data))
                           send)))))

(defn send-async
  ([producer data]
   (error/try-nom-ex :pulsar/producer-send-async PulsarClientException
                     "Failed to send async message to Pulsar"
                     (let [{:keys [^Producer instance]} producer]
                       (.sendAsync instance (serialize producer data)))))
  ([producer data opts]
   (error/try-nom-ex :pulsar/producer-send-async PulsarClientException
                     "Failed to send async message to Pulsar"
                     (let [{:keys [^Producer instance]} producer]
                       (.. instance
                           newMessage
                           (loadConf opts)
                           (value (serialize producer data))
                           sendAsync)))))

(defn close
  [producer]
  (error/try-nom-ex :pulsar/producer-close PulsarClientException
                    "Failed to close Pulsar producer connection"
                    (let [{:keys [^Producer instance]} producer]
                      (.close instance))))
