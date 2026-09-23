(ns com.repldriven.mono.keycloak.protocol)

;; Internal accessor protocol — `KeycloakIdentityProvider` extends it
;; so `core`'s helpers can pull config / cached-token atoms off the
;; record without depending on its field shape.
(defprotocol Client
  (-config [_])
  (-admin-token-atom [_])
  (-jwks-atom [_]))
