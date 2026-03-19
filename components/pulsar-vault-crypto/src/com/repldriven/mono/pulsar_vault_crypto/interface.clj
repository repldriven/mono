(ns com.repldriven.mono.pulsar-vault-crypto.interface
  (:require
    com.repldriven.mono.pulsar-vault-crypto.system
    [com.repldriven.mono.pulsar-vault-crypto.core :as core]))

(defn tenant-key-reader
  "Returns a CryptoKeyReader that reads tenant encryption keys from Vault on demand.
   vault-client - authenticated Vault client
   tenant-id    - tenant identifier, used to scope the Vault path
   mount        - Vault KV mount (default: \"secret\")"
  [vault-client tenant-id mount]
  (core/tenant-key-reader vault-client tenant-id mount))
