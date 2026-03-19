(ns com.repldriven.mono.pulsar.system.reader.edn
  (:require
    [com.repldriven.mono.env.interface :as env])
  (:import
    (org.apache.pulsar.client.api ConsumerCryptoFailureAction
                                  MessageId
                                  ProducerAccessMode
                                  Schema
                                  SubscriptionType)))

(defn- crypto-failure-action
  [_ tag value]
  (case value
    :CONSUME ConsumerCryptoFailureAction/CONSUME
    :DISCARD ConsumerCryptoFailureAction/DISCARD
    :FAIL ConsumerCryptoFailureAction/FAIL
    (throw (ex-info (format "Invalid value %s for tag %s" value tag)
                    {:tag tag :value value}))))

(defn- message-id
  [_ tag value]
  (case value
    :earliest MessageId/earliest
    :latest MessageId/latest
    (throw (ex-info (format "Invalid value %s for tag %s" value tag)
                    {:tag tag :value value}))))

(def ^:private schema-map
  {:BOOL Schema/BOOL
   :BYTEBUFFER Schema/BYTEBUFFER
   :BYTES Schema/BYTES
   :DATE Schema/DATE
   :DOUBLE Schema/DOUBLE
   :FLOAT Schema/FLOAT
   :INSTANT Schema/INSTANT
   :INT16 Schema/INT16
   :INT32 Schema/INT32
   :INT64 Schema/INT64
   :INT8 Schema/INT8
   :LOCAL_DATE Schema/LOCAL_DATE
   :LOCAL_DATE_TIME Schema/LOCAL_DATE_TIME
   :STRING Schema/STRING
   :TIME Schema/TIME
   :TIMESTAMP Schema/TIMESTAMP
   :AUTO_CONSUME (Schema/AUTO_CONSUME)
   :AUTO_PRODUCE_BYTES (Schema/AUTO_PRODUCE_BYTES)})

(defn- schema
  [_ tag value]
  (or (get schema-map value)
      (throw (ex-info (format "Invalid value %s for tag %s" value tag)
                      {:tag tag :value value}))))

(defn- subscription-type
  [_ tag value]
  (case value
    :Exclusive SubscriptionType/Exclusive
    :Failover SubscriptionType/Failover
    :Key_Shared SubscriptionType/Key_Shared
    :Shared SubscriptionType/Shared
    (throw (ex-info (format "Invalid value %s for tag %s" value tag)
                    {:tag tag :value value}))))

(defn- access-mode
  [_ tag value]
  (case value
    :Shared ProducerAccessMode/Shared
    :Exclusive ProducerAccessMode/Exclusive
    :WaitForExclusive ProducerAccessMode/WaitForExclusive
    (throw (ex-info (format "Invalid value %s for tag %s" value tag)
                    {:tag tag :value value}))))

;; edn-reader defmethods
(defmethod env/edn-reader 'pulsar-crypto-failure-action
  [opts tag value]
  (crypto-failure-action opts tag value))

(defmethod env/edn-reader 'pulsar-message-id
  [opts tag value]
  (message-id opts tag value))

(defmethod env/edn-reader 'pulsar-schema
  [opts tag value]
  (schema opts tag value))

(defmethod env/edn-reader 'pulsar-subscription-type
  [opts tag value]
  (subscription-type opts tag value))

(defmethod env/edn-reader 'pulsar-access-mode
  [opts tag value]
  (access-mode opts tag value))
