(ns com.repldriven.mono.pulsar.pulsar.reader
  (:refer-clojure :exclude [read])
  (:require
    [com.repldriven.mono.pulsar.pulsar.schemas :as schemas]
    [com.repldriven.mono.pulsar.pulsar.message :as message]
    [com.repldriven.mono.error.interface :refer [try-nom-ex]]
    [com.repldriven.mono.log.interface :as log]
    [clojure.core.async :as async]
    [clojure.java.data :as j])
  (:import
    (org.apache.pulsar.client.api
     Message
     PulsarClient
     PulsarClientException
     PulsarClientException$AlreadyClosedException
     Reader
     ReaderBuilder)
    (java.util Map)
    (java.util.concurrent TimeUnit)))

(defn create
  ^Reader [{:keys [^PulsarClient client conf schemas] :as opts}]
  (log/info "Creating Pulsar reader:" (:name opts))
  (try-nom-ex
   :pulsar/reader-create PulsarClientException
   "Failed to create Pulsar reader"
   (let [{:keys [cryptoKeyReader schema startMessageId]} conf
         manual-conf [:cryptoKeyReader :schema :startMessageId]
         conf-str-keys (into {} (map (fn [[k v]] [(name k) v]) conf))
         auto-conf (j/to-java
                    Map
                    (apply dissoc conf-str-keys (map name manual-conf)))
         instance (if (some? schema)
                    (.. client (newReader (schemas/resolve schemas schema)))
                    (.. client newReader))
         ^ReaderBuilder builder (.. instance (loadConf auto-conf))
         ^ReaderBuilder builder-with-conf
         (cond-> builder
                 (some? cryptoKeyReader)
                 (.cryptoKeyReader cryptoKeyReader)
                 (some? startMessageId)
                 (.startMessageId startMessageId))]
     (.create builder-with-conf))))

(defn read
  "Continuously read messages from a Pulsar reader and put
  them on a channel. Returns {:c chan :stop chan}. Send
  anything to :stop to stop reading."
  [^Reader reader timeout-ms]
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
                                  (.. reader
                                      (readNext timeout-ms
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
                "than the reader produced (e.g. via async/take), nobody reads"
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

(defn close
  [^Reader reader]
  (try-nom-ex :pulsar/reader-close PulsarClientException
              "Failed to close Pulsar reader connection" (.close reader)))
