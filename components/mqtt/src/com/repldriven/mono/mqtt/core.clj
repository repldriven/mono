(ns com.repldriven.mono.mqtt.core
  (:require
    [com.repldriven.mono.mqtt.client :as client]))

(defn publish [c topic payload] (client/publish c topic payload))

(defn subscribe
  [c topics-and-qos handler-fn]
  (client/subscribe c topics-and-qos handler-fn))

(defn unsubscribe [c topics] (client/unsubscribe c topics))

(defn producer
  [{:keys [client conf]}]
  (let [{:keys [topic qos]} conf]
    {:client client :topic topic :qos (or qos 0)}))

(defn consumer
  [{:keys [client conf]}]
  (let [{:keys [topic qos]} conf]
    {:client client :topic topic :qos (or qos 0)}))
