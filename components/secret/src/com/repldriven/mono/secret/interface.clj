(ns com.repldriven.mono.secret.interface
  "Provider-neutral secret retrieval. Application code reads secrets
  through `get-secret`, passing a secret provider the system wires per
  environment: `pass` in dev, GCP Secret Manager in prod, an in-memory
  fixture in tests. Callers depend only on this interface, never on
  which provider is behind it. Registers the provider component kinds
  through this brick's `system` namespace."
  (:require
    com.repldriven.mono.secret.system

    [com.repldriven.mono.secret.protocol :as protocol]
    [com.repldriven.mono.error.interface :as error]))

(defn get-secret
  "Read the secret bound to `secret-id` from `provider`.

  `secret-id` is a provider-neutral keyword (e.g. `:clearbank/api-key`)
  that each provider maps to its own backend reference. Returns the
  secret value as a byte array, or an `:error/anomaly` if the id is
  unknown or the backend read fails.

  Args:
  - provider: a secret provider instance from the system.
  - secret-id: provider-neutral secret keyword."
  [provider secret-id]
  (error/try-nom :secret/get-secret-failed
                 (str "Failed to read secret: " secret-id)
                 (protocol/-get-secret provider secret-id)))
