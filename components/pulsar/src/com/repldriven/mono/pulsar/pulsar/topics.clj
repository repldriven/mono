(ns com.repldriven.mono.pulsar.pulsar.topics
  (:require
    [com.repldriven.mono.pulsar.pulsar.schemas :as schemas]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log])
  (:import
    (org.apache.pulsar.client.admin PulsarAdmin Schemas Topics)
    (org.apache.pulsar.common.naming TopicName)
    (org.apache.pulsar.common.protocol.schema PostSchemaPayload)))

(defn- create-topic-schema
  [^PulsarAdmin admin ^String fully-qualified-topic-name
   ^PostSchemaPayload schema]
  (log/info "Creating schema for topic:" fully-qualified-topic-name)
  (.createSchema ^Schemas (.schemas admin) fully-qualified-topic-name schema))

(defn- create
  [^PulsarAdmin admin fully-qualified-topic-name &
   {:keys [partitions schema] :or {partitions 1}}]
  (let [^TopicName topic-name (TopicName/get fully-qualified-topic-name)
        fully-qualified-namespace-name (.getNamespace topic-name)
        ^Topics topics (.topics admin)
        domain (.getDomain topic-name)
        topic-names (.getList topics fully-qualified-namespace-name domain)]
    (when-not (contains? (set topic-names) fully-qualified-topic-name)
      (log/info "Creating topic:" fully-qualified-topic-name)
      (.createPartitionedTopic topics fully-qualified-topic-name partitions)
      (when (some? schema)
        (create-topic-schema admin fully-qualified-topic-name schema)))))

(defn create-topics
  [{:keys [^PulsarAdmin admin schemas topics]}]
  (log/info "Creating Pulsar topics:" (map :topic topics))
  (error/try-nom
   :pulsar/topics-create
   "Failed to create Pulsar topics"
   (doall (mapv (fn [{:keys [topic] :as opts}]
                  (let [resolved-opts (update opts
                                              :schema
                                              #(schemas/resolve-payload schemas
                                                                        %))]
                    (create admin topic (dissoc resolved-opts :topic))))
                topics))))
