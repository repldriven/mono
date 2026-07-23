(ns com.repldriven.mono.identity-provider.interface
  "Per-tenant service-account provisioning and JWT verification.
  Substrate brick — adapter-agnostic and domain-agnostic. The
  bundled `LocalIdentityProvider` (in `local`) is suitable for fast
  brick tests; external SDK adapters (e.g., the `keycloak` brick's
  `KeycloakIdentityProvider`) plug into the same `IdentityProvider`
  protocol.

  Callers depend on this interface — never on a specific adapter —
  and the system YAML wires whichever implementation matches the
  environment."
  (:require
    com.repldriven.mono.identity-provider.system.core

    [com.repldriven.mono.identity-provider.local :as local]
    [com.repldriven.mono.identity-provider.protocol :as protocol]))

;; Re-export so external implementers can pull the protocol off the
;; interface namespace — same pattern as `message-bus.interface`
;; does for Producer/Consumer.
(def IdentityProvider protocol/IdentityProvider)

(def
  ^{:doc
    "Build an in-memory `LocalIdentityProvider` for fast brick tests
  that need a real IDP without a system or external service. `config`
  may carry `:issuer`. Production wires an adapter via the system YAML."}
  local-provider
  local/->client)

(defn create-service-account
  "Create a service-account client for a bank. Returns
  `{:client-id … :client-secret …}` (the secret is only available
  at creation time) or an anomaly.

  Args:
  - client: identity-provider component.
  - data: map with `:bank-id`, optional `:name`, and `:audience`
    (the JWT `aud` claim to stamp on tokens for this client)."
  [client data]
  (protocol/-create-service-account client data))

(defn revoke-service-account
  "Delete the service-account client for `bank-id`. Idempotent."
  [client bank-id]
  (protocol/-revoke-service-account client bank-id))

(defn rotate-secret
  "Issue a fresh `client_secret` for `bank-id`. Returns
  `{:client-id … :client-secret …}` or an anomaly."
  [client bank-id]
  (protocol/-rotate-secret client bank-id))

(defn update-service-account-audience
  "Point `bank-id`'s service-account client at `audience` — the JWT
  `aud` claim its tokens should carry going forward. Target-state
  idempotent: a redelivered call converges on the same result.
  Returns `{:client-id …}` or an anomaly."
  [client bank-id audience]
  (protocol/-update-service-account-audience client bank-id audience))

(defn exchange-client-credentials
  "Run the OAuth2 `client_credentials` flow. Returns the raw token
  response with snake-case keys (`:access_token`, `:expires_in`,
  `:token_type`, `:scope`) — shape mirrors Keycloak's so callers
  forward it untouched — or an anomaly.

  Args:
  - client: identity-provider component.
  - creds: map with `:client-id`, `:client-secret`, and optional
    `:scope`."
  [client creds]
  (protocol/-exchange-client-credentials client creds))

(defn verify-token
  "Validate a JWT. Returns the claims map or an
  `:auth/unauthenticated` rejection.

  Args:
  - client: identity-provider component.
  - jwt-string: the raw `Bearer` token value.
  - opts: map with `:expected-audiences` (set of strings; token's
    `aud` must intersect)."
  [client jwt-string opts]
  (protocol/-verify-token client jwt-string opts))

(defn get-jwks
  "Return the JWKS (signing keys) so consumers can verify tokens
  offline."
  [client]
  (protocol/-get-jwks client))

(defn get-issuer
  "Return the issuer URL (`iss` claim value)."
  [client]
  (protocol/-get-issuer client))
