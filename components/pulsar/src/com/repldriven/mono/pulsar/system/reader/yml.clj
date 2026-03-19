(ns com.repldriven.mono.pulsar.system.reader.yml
  (:require
    [com.repldriven.mono.env.interface :as env]))

;; yml-reader defmethods
(defmethod env/yml-reader :!pulsar/crypto-failure-action
  [{:keys [value]}]
  (symbol (str "#pulsar-crypto-failure-action " (pr-str (keyword value)))))

(defmethod env/yml-reader :!pulsar/message-id
  [{:keys [value]}]
  (symbol (str "#pulsar-message-id " (pr-str (keyword value)))))

(defmethod env/yml-reader :!pulsar/schema
  [{:keys [value]}]
  (symbol (str "#pulsar-schema " (pr-str (keyword value)))))

(defmethod env/yml-reader :!pulsar/subscription-type
  [{:keys [value]}]
  (symbol (str "#pulsar-subscription-type " (pr-str (keyword value)))))
