(ns com.repldriven.mono.kafka.kafka.config
  "Turning a config map into the java.util.Properties Kafka wants.

  Kafka takes configuration as string-keyed properties — `bootstrap.servers`,
  `group.id` — rather than through a builder, so a YAML map reaches the client
  almost unchanged. Keys are stringified without munging: write them exactly as
  Kafka documents them, dots and all.")

(defn ->properties
  ^java.util.Properties [config]
  (let [props (java.util.Properties.)]
    (doseq [[k v] config]
      (.put props (name k) (if (keyword? v) (name v) (str v))))
    props))
