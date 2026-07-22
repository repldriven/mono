(ns com.repldriven.mono.vault.interface
  "Thin wrapper around `amperity/vault-clj` that exposes a tiny surface
  for client construction, authentication, and KV-v2 secret reads.
  Registers a `:vault/client` donut.system component."
  (:require
    com.repldriven.mono.vault.system
    [com.repldriven.mono.vault.core :as core]))

(defn create-client
  "Construct an unauthenticated Vault HTTP client for `uri`.

  Args:
  - uri: Vault server URI."
  [uri]
  (core/create-client uri))

(defn authenticate-client!
  "Authenticate `client` using `auth-type` (e.g. `:token`, `:approle`)
  and the associated `credentials`. Mutates the client in place.

  Args:
  - client: a Vault client from `create-client`.
  - auth-type: keyword identifying the auth backend.
  - credentials: backend-specific credential map or token string."
  [client auth-type credentials]
  (core/authenticate-client! client auth-type credentials))

(defn read-secret
  "Read a KV-v2 secret at `path` under `mount` using an authenticated
  `client`. Trailing `opts` are forwarded to `vault-clj`.

  Args:
  - client: an authenticated Vault client.
  - mount: KV-v2 mount name (e.g. \"secret\").
  - path: secret path beneath the mount.
  - opts: optional vault-clj read options."
  [client mount path & opts]
  (apply core/read-secret client mount path opts))
