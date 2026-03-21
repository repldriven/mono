(ns com.repldriven.mono.mqtt.client
  (:require
    [com.repldriven.mono.error.interface :refer [try-nom]]
    [com.repldriven.mono.log.interface :as log]
    [clojurewerkz.machine-head.client :as mh]))

(defn- strip-prefix [s] (if (.startsWith s "mqtt://") (subs s 7) s))

(defn publish
  "Publish a message to an MQTT topic. Returns nil on success or an anomaly on failure."
  [client topic payload]
  (log/debugf "mqqt.client/publish [client=%s, topic=%s, payload=%s]"
              client
              topic
              payload)
  (try-nom :mqtt/publish
           (format "Failed to publish to topic %s" topic)
           (mh/publish client (strip-prefix topic) payload)
           nil))

(defn subscribe
  "Subscribe to MQTT topics. Returns nil on success or an anomaly on failure."
  [client topics-and-qos handler-fn]
  (log/debugf
   "mqqt.client/subscribe [client=%s, topic-and-qos=%s, handler-fn=%s]"
   client
   topics-and-qos
   handler-fn)
  (try-nom
   :mqtt/subscribe
   (format "Failed to subscribe to topics %s" topics-and-qos)
   (mh/subscribe client (update-keys topics-and-qos strip-prefix) handler-fn)
   nil))

(defn unsubscribe
  "Unsubscribe from MQTT topics. Returns nil on success or an anomaly on failure."
  [client topics]
  (log/debugf "mqqt.client/usubscribe [client=%s, topics=%s]" client topics)
  (try-nom :mqtt/unsubscribe
           (format "Failed to unsubscribe from topics %s" topics)
           (mh/unsubscribe client (mapv strip-prefix topics))
           nil))
