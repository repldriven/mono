(ns com.repldriven.mono.kafka.kafka.topics
  (:require
    [com.repldriven.mono.kafka.kafka.config :as config]

    [com.repldriven.mono.error.interface :refer [try-nom]]
    [com.repldriven.mono.log.interface :as log])
  (:import
    (org.apache.kafka.clients.admin AdminClient NewTopic)
    (org.apache.kafka.common.errors TopicExistsException)))

(defn create-admin
  [{:keys [bootstrap-servers] :as conf}]
  (log/info "Creating Kafka admin client:" bootstrap-servers)
  (try-nom :kafka/admin-create
           "Failed to create Kafka admin client"
           (AdminClient/create
            (config/->properties (merge {"bootstrap.servers"
                                         bootstrap-servers}
                                        (dissoc conf :bootstrap-servers))))))

(defn close-admin
  [^AdminClient admin]
  (try-nom :kafka/admin-close
           "Failed to close Kafka admin client"
           (when admin (.close admin))))

(defn- ->new-topic
  ^NewTopic
  [{:keys [topic partitions replication-factor]
    :or {partitions 1 replication-factor 1}}]
  (NewTopic. ^String topic (int partitions) (short replication-factor)))

(defn create
  "Create `topics` if they do not exist. Idempotent: a topic that already
  exists is left alone, which matters because a test system and a migrator
  may both declare the same topic."
  [^AdminClient admin topics]
  (try-nom
   :kafka/topics-create
   "Failed to create Kafka topics"
   (let [wanted (map ->new-topic topics)
         existing (set (.get (.names (.listTopics admin))))
         missing (remove #(contains? existing (.name ^NewTopic %)) wanted)]
     (when (seq missing)
       (log/info "Creating Kafka topics:" (mapv #(.name ^NewTopic %) missing))
       (doseq [^NewTopic t missing]
         ;; Between listTopics and here another process may have created it
         ;; — the same race pulsar's partitioned-topic check guards
         ;; against.
         (try (.get (.all (.createTopics admin [t])))
              (catch Exception e
                (if (instance? TopicExistsException (.getCause e))
                  (log/info "Kafka topic already exists:" (.name t))
                  ;; nosemgrep: no-raw-throw
                  (throw e))))))
     (mapv #(.name ^NewTopic %) wanted))))
