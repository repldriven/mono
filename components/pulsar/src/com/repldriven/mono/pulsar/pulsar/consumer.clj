(ns com.repldriven.mono.pulsar.pulsar.consumer
  (:require
    [com.repldriven.mono.pulsar.pulsar.schemas :as schemas]
    [com.repldriven.mono.pulsar.pulsar.message :as message]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.error.interface :refer [try-nom-ex]]
    [clojure.core.async :as async]
    [clojure.java.data :as j])
  (:import
    (java.util Map)
    (java.util.concurrent TimeUnit)
    (org.apache.pulsar.client.api
     Consumer
     ConsumerBuilder
     Message
     PulsarClient
     PulsarClientException
     PulsarClientException$AlreadyClosedException)))

(defn create
  ^Consumer [{:keys [^PulsarClient client conf schemas] :as opts}]
  (log/info "Creating Pulsar consumer:" (:name opts))
  (try-nom-ex
   :pulsar/consumer-create PulsarClientException
   "Failed to create Pulsar consumer"
   (let [{:keys [cryptoKeyReader schema]} conf
         manual-conf [:cryptoKeyReader :schema]
         conf-str-keys (into {} (map (fn [[k v]] [(name k) v]) conf))
         auto-conf (j/to-java
                    Map
                    (apply dissoc conf-str-keys (map name manual-conf)))
         instance (if (some? schema)
                    (.. client
                        (newConsumer (schemas/resolve schemas schema)))
                    (.. client newConsumer))
         ^ConsumerBuilder builder (.. instance (loadConf auto-conf))
         ^ConsumerBuilder builder-with-conf
         (cond-> builder
                 (some? cryptoKeyReader)
                 (.cryptoKeyReader cryptoKeyReader))]
     (.subscribe builder-with-conf))))

(defn receive
  "Continuously receive messages from a Pulsar consumer
  and put them on a channel. Returns {:c chan :stop chan}.
  Send anything to :stop to stop receiving."
  [^Consumer consumer timeout-ms]
  (let [c (async/chan)
        stop (async/chan 1)]
    (async/thread
     (try
       (loop []
         (let [[v port]
               (async/alts!!
                [stop
                 (async/thread
                  (try (when-let [^Message msg
                                  (.. consumer
                                      (receive timeout-ms
                                               TimeUnit/MILLISECONDS))]
                         msg)
                       (catch PulsarClientException$AlreadyClosedException
                         _
                         ::closed)))])]
           (cond
            (or (= port stop) (= v ::closed))
            nil
            (some? v)
            (do
              (comment
                "Race the put against stop. If the caller took fewer messages"
                "than the consumer produced (e.g. via async/take), nobody reads"
                "c anymore and a plain >!! would block the loop — making"
                ">!! stop :stop deadlock too.")
              (let [[_ p] (async/alts!!
                           [[c {:message v :data (message/deserialize v)}]
                            stop])]
                (when (not= p stop) (recur))))
            :else
            (recur))))
       (finally (async/close! c) (async/close! stop))))
    {:c c :stop stop}))

(defn acknowledge
  "Acknowledge a message. Returns nil on success or an anomaly on failure."
  [^Consumer consumer ^Message message]
  (try-nom-ex :pulsar/consumer-acknowledge PulsarClientException
              "Failed to acknowledge Pulsar consumer message"
              (do (.acknowledge consumer message) nil)))

(defn close
  [^Consumer consumer]
  (try-nom-ex :pulsar/consumer-close PulsarClientException
              "Failed to close Pulsar consumer connection" (.close
                                                            consumer)))
