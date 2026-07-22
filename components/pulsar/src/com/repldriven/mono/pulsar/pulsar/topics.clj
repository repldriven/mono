(ns com.repldriven.mono.pulsar.pulsar.topics
  (:require
    [clojure.set :as set]
    [com.repldriven.mono.pulsar.pulsar.schemas :as schemas]
    [com.repldriven.mono.error.interface :as error :refer [try-nom]]
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
        ;; We always create partitioned topics, so the existence
        ;; check must query the partitioned-topic list — `.getList`
        ;; only returns NON-partitioned topics, which would let
        ;; `.createPartitionedTopic` race into a 409 on re-run.
        topic-names (.getPartitionedTopicList topics
                                              fully-qualified-namespace-name)]
    (when-not (contains? (set topic-names) fully-qualified-topic-name)
      (log/info "Creating topic:" fully-qualified-topic-name)
      (.createPartitionedTopic topics fully-qualified-topic-name partitions)
      (when (some? schema)
        (create-topic-schema admin fully-qualified-topic-name schema)))
    ;; `createPartitionedTopic` only writes the partitioned-topic
    ;; metadata; the per-partition physical topics
    ;; (`<topic>-partition-0`, …) are normally materialised lazily
    ;; on first publish/subscribe. With our namespace policy
    ;; `allowAutoTopicCreation: false`, that lazy creation is
    ;; suppressed and producers/consumers crash with
    ;; `TopicDoesNotExistException` against the `-partition-N`
    ;; form. Run `createMissedPartitions` unconditionally — outside
    ;; the existence guard — because it's idempotent (no-op if all
    ;; partitions already exist) and crucially also fixes already-
    ;; declared topics from previous migrator runs that predate this
    ;; line.
    (.createMissedPartitions topics fully-qualified-topic-name)))

(defn- expected-partition-names
  "Pulsar names physical partitions `<topic>-partition-N`. Given a
  declared `{:topic … :partitions N}` entry from the manifest,
  return the set of physical names that must be present in the
  namespace for the topology to be usable."
  [{:keys [topic partitions] :or {partitions 1}}]
  (set (map #(str topic "-partition-" %) (range partitions))))

(defn- audit-partitions
  "After all `create` calls, enumerate the namespace's physical
  topics and verify every declared partition is present. Pulsar's
  `createMissedPartitions` is observed to silently no-op on
  occasion (single-process dev brokers, possibly under contention
  with the directory-layer init), leaving a partitioned-topic with
  metadata `partitions: 1` but no `-partition-0` actually
  materialised. Downstream services then crash on `subscribe` /
  `createProducer` with `TopicDoesNotExistException`. Refuse to
  declare migration successful if the topology isn't fully usable."
  [^PulsarAdmin admin topics]
  (let [namespaces (set (map #(.getNamespace
                               (TopicName/get ^String (:topic %)))
                             topics))
        ;; `getList` returns the physical topics, including each
        ;; partition under its `<topic>-partition-N` name. Union
        ;; across every namespace touched by the manifest (in
        ;; practice always one, but no reason to assume).
        present (into #{}
                      (mapcat #(.getList ^Topics (.topics admin) %))
                      namespaces)
        expected (into #{} (mapcat expected-partition-names) topics)
        missing (vec (sort (set/difference expected present)))]
    (if (seq missing)
      (error/fail :pulsar/topics-audit
                  {:message
                   "Pulsar topology missing partition(s) after creation"
                   :missing missing
                   :expected-count (count expected)
                   :present-count (count (set/intersection expected
                                                           present))})
      (do (log/info "Pulsar topology audit OK; partitions present:"
                    (count expected))
          :ok))))

(defn create-topics
  [{:keys [^PulsarAdmin admin schemas topics]}]
  (log/info "Creating Pulsar topics:" (map :topic topics))
  (try-nom
   :pulsar/topics-create
   "Failed to create Pulsar topics"
   (doall (mapv (fn [{:keys [topic] :as opts}]
                  (let [resolved-opts (update opts
                                              :schema
                                              #(schemas/resolve-payload schemas
                                                                        %))]
                    (create admin topic (dissoc resolved-opts :topic))))
                topics))
   (audit-partitions admin topics)))
