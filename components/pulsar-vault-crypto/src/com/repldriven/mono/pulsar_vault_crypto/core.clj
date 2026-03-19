(ns com.repldriven.mono.pulsar-vault-crypto.core
  (:require
    [com.repldriven.mono.vault.interface :as vault]
    [com.repldriven.mono.log.interface :as log])
  (:import
    (org.apache.pulsar.client.api CryptoKeyReader EncryptionKeyInfo)))

(defn tenant-key-reader
  "Returns a CryptoKeyReader that reads tenant encryption keys from Vault on demand.
   Keys are stored as base64-encoded PEM bytes at:
   <mount>/tenants/<tenant-id>/keys/<key-name> with fields :public and :private."
  [vault-client tenant-id mount]
  (let
    [decoder (java.util.Base64/getDecoder)
     read-key
     (fn [key-name field]
       (log/debugf "pulsar-vault-crypto: reading %s key [tenant=%s, key=%s]"
                   (name field)
                   tenant-id
                   key-name)
       (let
         [path (str "tenants/" tenant-id "/keys/" key-name)
          secret
          (try
            (vault/read-secret vault-client mount path)
            (catch Exception e
              (log/warnf
               "pulsar-vault-crypto: vault read failed [tenant=%s, key=%s]: %s"
               tenant-id
               key-name
               (.getMessage e))
              nil))]
         (when secret
           (doto (EncryptionKeyInfo.)
             (.setKey (.decode decoder (get secret field)))))))]
    (reify
     CryptoKeyReader
       (getPublicKey [_ key-name _metadata] (read-key key-name :public))
       (getPrivateKey [_ key-name _metadata] (read-key key-name :private)))))
