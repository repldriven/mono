(ns com.repldriven.mono.pulsar-vault-crypto.core
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.vault.interface :as vault])
  (:import
    (org.apache.pulsar.client.api CryptoKeyReader EncryptionKeyInfo)))

(defn tenant-key-reader
  [vault-client tenant-id mount]
  (let
    [decoder (java.util.Base64/getDecoder)
     read-key
     (fn [key-name field]
       (log/debugf "pulsar-vault-crypto: reading %s key [tenant=%s, key=%s]"
                   (name field)
                   tenant-id
                   key-name)
       ;; Pulsar calls this from its own threads and wants an
       ;; EncryptionKeyInfo or nil, so this is a boundary where anomalies
       ;; stop: a failure is logged and becomes nil, which Pulsar reads as
       ;; "no key". Decoding is inside the guard because a secret holding
       ;; malformed base64 throws IllegalArgumentException.
       (let [path (str "tenants/" tenant-id "/keys/" key-name)
             secret (vault/read-secret vault-client mount path)]
         (if (error/anomaly? secret)
           (do (log/warnf
                "pulsar-vault-crypto: vault read failed [tenant=%s, key=%s]: %s"
                tenant-id
                key-name
                (:message (error/payload secret)))
               nil)
           (try
             (doto (EncryptionKeyInfo.)
               (.setKey (.decode decoder (get secret field))))
             (catch Exception e
               (log/warnf
                "pulsar-vault-crypto: key decode failed [tenant=%s, key=%s]: %s"
                tenant-id
                key-name
                (.getMessage e))
               nil)))))]
    (reify
     CryptoKeyReader
       (getPublicKey [_ key-name _metadata] (read-key key-name :public))
       (getPrivateKey [_ key-name _metadata] (read-key key-name :private)))))
