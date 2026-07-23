(ns com.repldriven.mono.identity-provider.protocol)

;; Substrate protocol every identity-provider implementation extends.
;; The bundled `LocalIdentityProvider` (in this brick) and the
;; `KeycloakIdentityProvider` (in the `keycloak` brick) both implement
;; this inline at their defrecord — the same pattern message-bus uses
;; for Producer/Consumer with LocalProducer/PulsarProducer.
(defprotocol IdentityProvider
  (-create-service-account [this data])
  (-revoke-service-account [this bank-id])
  (-rotate-secret [this bank-id])
  (-update-service-account-audience [this bank-id audience])
  (-exchange-client-credentials [this creds])
  (-verify-token [this jwt-string opts])
  (-get-jwks [this])
  (-get-issuer [this]))
