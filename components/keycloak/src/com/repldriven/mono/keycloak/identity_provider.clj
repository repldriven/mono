(ns com.repldriven.mono.keycloak.identity-provider
  "`KeycloakIdentityProvider` — a defrecord that holds the Keycloak
  admin-token + JWKS atoms plus the realm config, and implements the
  `identity-provider` brick's `IdentityProvider` protocol inline by
  orchestrating calls into this brick's `core` namespace.

  Audience handling is domain-agnostic: callers pass an `:audience`
  string on `create-service-account` and the adapter attaches it as
  the client-scope name on the new client's `defaultClientScopes`.
  The realm import owns the actual audience mapper for that scope, so
  tokens minted by the client carry the right `aud` claim
  automatically."
  (:require
    [com.repldriven.mono.identity-provider.interface :as identity-provider]
    [com.repldriven.mono.keycloak.core :as core]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]

    [buddy.core.keys :as buddy-keys]
    [buddy.sign.jws :as jws]
    [buddy.sign.jwt :as jwt]))

(defn- find-jwk
  [jwks kid]
  (some #(when (= kid (:kid %)) %) (:keys jwks)))

(defn- verify-token-impl
  [client jwt-string {:keys [expected-audiences]}]
  (try
    (let [header (jws/decode-header jwt-string)
          kid (some-> header
                      :kid)
          jwks (core/jwks! client)]
      (if (error/anomaly? jwks)
        jwks
        (let [jwk (or (find-jwk jwks kid)
                      ;; kid not in cache — force-refresh once in case
                      ;; Keycloak rotated.
                      (find-jwk (core/jwks! client true) kid))]
          (if-not jwk
            (error/reject :auth/unauthenticated
                          {:message "Token signing key not recognised"
                           :kid kid})
            (let [public-key (buddy-keys/jwk->public-key jwk)
                  ;; Tokens minted by Keycloak embed iss = frontchannel
                  ;; `hostname.hostname/realms/<realm>`, which can differ
                  ;; from the realm URL bank-api derives from base-url
                  ;; (e.g. base-url = in-cluster Service for fast
                  ;; admin REST calls, but the deployment's public
                  ;; hostname is what ends up in tokens). An explicit
                  ;; `:expected-issuer` on the client config overrides
                  ;; the base-url-derived default for this check.
                  expected-iss (or (:expected-issuer (core/-config client))
                                   (core/issuer client))
                  claims (jwt/unsign jwt-string
                                     public-key
                                     {:alg :rs256
                                      :iss expected-iss})]
              (if (and (seq expected-audiences)
                       (not (some expected-audiences
                                  (cond-> (:aud claims)
                                          (string? (:aud claims))
                                          vector))))
                (error/reject :auth/unauthenticated
                              {:message "Token audience not accepted"
                               :aud (:aud claims)})
                claims))))))
    (catch Exception e
      (error/reject :auth/unauthenticated
                    {:message (str "Token verification failed: "
                                   (.getMessage e))}))))

(defrecord KeycloakIdentityProvider [config admin-token jwks]
  core/Client
    (-config [_] config)
    (-admin-token-atom [_] admin-token)
    (-jwks-atom [_] jwks)
  identity-provider/IdentityProvider
    (-create-service-account [this {:keys [bank-id name audience]}]
      (let-nom> [_ (core/create-client
                    this
                    {:bank-id bank-id :name name :audience audience})
                 result (core/client-secret this bank-id)]
        result))
    (-revoke-service-account [this bank-id] (core/delete-client this bank-id))
    (-rotate-secret [this bank-id] (core/regenerate-secret this bank-id))
    (-update-service-account-audience [this bank-id audience]
      (core/update-client-audience this bank-id audience))
    (-exchange-client-credentials [this creds]
      (core/exchange-client-credentials this creds))
    (-verify-token [this jwt-string opts]
      (verify-token-impl this jwt-string opts))
    (-get-jwks [this] (core/jwks! this))
    ;; Match the verifier's expected iss (`:expected-issuer` override,
    ;; else base-url-derived) so provider selection keys on the same
    ;; issuer the token actually carries — base-url is the in-cluster
    ;; Service, but tokens embed the public frontchannel hostname.
    (-get-issuer [this] (or (:expected-issuer config) (core/issuer this))))

(defn ->client
  "Build a `KeycloakIdentityProvider`. `config` carries `:base-url`,
  `:realm`, `:admin-client-id`, `:admin-client-secret`, and an
  optional `:expected-issuer` for token validation when the
  base-url and the token's `iss` claim disagree (e.g. internal
  Service URL for backchannel admin REST + public Keycloak
  hostname embedded as iss)."
  [{:keys [base-url realm admin-client-id admin-client-secret expected-issuer]}]
  (->KeycloakIdentityProvider
   {:base-url base-url
    :realm realm
    :admin-client-id admin-client-id
    :admin-client-secret admin-client-secret
    :expected-issuer expected-issuer}
   (atom nil)
   (atom nil)))
