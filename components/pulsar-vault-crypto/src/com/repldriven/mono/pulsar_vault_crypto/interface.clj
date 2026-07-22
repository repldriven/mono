(ns com.repldriven.mono.pulsar-vault-crypto.interface
  "Pulsar `CryptoKeyReader` backed by Vault KV-v2. Keys are read lazily
  per request from `<mount>/tenants/<tenant-id>/keys/<key-name>`,
  base64-decoded into `EncryptionKeyInfo`. Registers a
  `:pulsar-vault-crypto/tenant-key-reader` donut.system component."
  (:require
    com.repldriven.mono.pulsar-vault-crypto.system
    [com.repldriven.mono.pulsar-vault-crypto.core :as core]))

(defn tenant-key-reader
  "Build a `CryptoKeyReader` that resolves tenant encryption keys from
  Vault on demand. Vault read failures are logged and surfaced as nil
  keys (Pulsar then errors at use site).

  Args:
  - vault-client: authenticated Vault client.
  - tenant-id: tenant identifier scoping the Vault path.
  - mount: Vault KV mount (e.g. \"secret\")."
  [vault-client tenant-id mount]
  (core/tenant-key-reader vault-client tenant-id mount))
