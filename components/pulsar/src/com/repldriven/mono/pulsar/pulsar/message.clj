(ns com.repldriven.mono.pulsar.pulsar.message
  (:require
    [com.repldriven.mono.pulsar.pulsar.generic-record :as
     generic-record]
    [com.repldriven.mono.error.interface :as error])
  (:import
    (java.util Optional)
    (org.apache.pulsar.client.api Message)
    (org.apache.pulsar.client.api.schema GenericRecord)
    (org.apache.pulsar.common.api EncryptionContext)))

(defn encrypted?
  "Returns true if the message is still encrypted (decryption failed)."
  [^Message msg]
  (let [^Optional encryption-ctx (.getEncryptionCtx msg)]
    (and (.isPresent encryption-ctx)
         (.isEncrypted ^EncryptionContext (.get encryption-ctx)))))

(defn deserialize
  "Deserializes a Pulsar message to a Clojure map.

  Returns an anomaly if the message is encrypted or if the
  value is not a GenericRecord (AUTO_CONSUME schema)."
  [^Message msg]
  (if (encrypted? msg)
    (error/fail :pulsar/message-decrypt "Message cannot be decrypted")
    (let [value (.getValue msg)]
      (if (instance? GenericRecord value)
        (generic-record/deserialize value)
        (error/fail :pulsar/message-format
                    {:message "Expected GenericRecord"
                     :actual (type value)})))))
