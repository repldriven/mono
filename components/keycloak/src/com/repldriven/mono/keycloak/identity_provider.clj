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
    [com.repldriven.mono.keycloak.protocol :as protocol]

    [com.repldriven.mono.cache.interface :as cache]
    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.utility.interface :as util]

    [buddy.core.keys :as buddy-keys]
    [buddy.sign.jws :as jws]
    [buddy.sign.jwt :as jwt]))

(defn- find-jwk
  [jwks kid]
  (some #(when (= kid (:kid %)) %) (:keys jwks)))

(defn- verify-signature
  [client jwt-string]
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
            ;; Tokens minted by Keycloak embed iss = frontchannel
            ;; `hostname.hostname/realms/<realm>`, which can differ
            ;; from the realm URL bank-api derives from base-url
            ;; (e.g. base-url = in-cluster Service for fast
            ;; admin REST calls, but the deployment's public
            ;; hostname is what ends up in tokens). An explicit
            ;; `:expected-issuer` on the client config overrides
            ;; the base-url-derived default for this check.
            (jwt/unsign jwt-string
                        (buddy-keys/jwk->public-key jwk)
                        {:alg :rs256
                         :iss (or (:expected-issuer (protocol/-config client))
                                  (core/issuer client))})))))
    (catch Exception e
      (error/reject :auth/unauthenticated
                    {:message (str "Token verification failed: "
                                   (.getMessage e))}))))

(defn- unexpired?
  [claims]
  (let [{:keys [exp]} claims]
    (and (number? exp) (< (quot (util/now) 1000) exp))))

(defn- verified-claims
  [client jwt-string]
  (let [verified (:verified client)
        k (when verified (encryption/hash-token jwt-string))]
    (if (or (nil? k) (error/anomaly? k))
      (verify-signature client jwt-string)
      (let [refused (volatile! nil)
            claims (cache/lookup verified
                                 k
                                 (fn []
                                   (let [result (verify-signature client
                                                                  jwt-string)]
                                     (if (error/anomaly? result)
                                       (do (vreset! refused result) nil)
                                       result))))]
        (cond @refused
              @refused

              (unexpired? claims)
              claims

              :else
              (do (cache/evict verified k)
                  (verify-signature client jwt-string)))))))

(defn- verify-token-impl
  [client jwt-string {:keys [expected-audiences]}]
  (let [claims (verified-claims client jwt-string)]
    (cond (error/anomaly? claims)
          claims

          (and (seq expected-audiences)
               (not (some expected-audiences
                          (cond-> (:aud claims)
                                  (string? (:aud claims))
                                  vector))))
          (error/reject :auth/unauthenticated
                        {:message "Token audience not accepted"
                         :aud (:aud claims)})

          :else
          claims)))

(defrecord KeycloakIdentityProvider [config admin-token jwks verified]
  protocol/Client
    (-config [_] config)
    (-admin-token-atom [_] admin-token)
    (-jwks-atom [_] jwks)
  identity-provider/IdentityProvider
    ;; The client alone: a secret is minted by `rotate-secret` when a
    ;; caller wants one, so creating does not spend two Admin calls
    ;; reading a secret nothing holds on to.
    (-create-service-account [this {:keys [bank-id name audience]}]
      (core/create-client this
                          {:bank-id bank-id :name name :audience audience}))
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

(def default-verified-token-ttl-ms
  "How long a verified token's claims are kept, at most: never past the
  token's own `exp`."
  60000)

(def default-verified-token-max-entries
  "How many verified tokens are kept at once, the oldest going first."
  10000)

(defn ->client
  "Build a `KeycloakIdentityProvider`. `config` carries `:base-url`,
  `:realm`, `:admin-client-id`, one of `:admin-client-secret` or
  `:admin-client-private-key-file` (the latter authenticating with
  `private_key_jwt` and winning when both are present), and an
  optional `:expected-issuer` for when the base-url and the realm
  URL Keycloak knows itself by disagree (e.g. internal Service URL
  for backchannel admin REST + public Keycloak hostname embedded as
  iss). That issuer is both the `iss` inbound token verification
  expects and the audience an outbound client assertion claims.

  A token whose signature and issuer have been verified is kept, by
  the SHA-256 of its exact string, for `:verified-token-ttl-ms` or until its `exp`,
  whichever comes first, up to `:verified-token-max-entries` tokens;
  zero keeps none. The audience is checked on every call."
  [config]
  (let [{:keys [base-url realm admin-client-id admin-client-secret
                admin-client-private-key-file expected-issuer
                verified-token-ttl-ms verified-token-max-entries]}
        config
        ttl-ms (or verified-token-ttl-ms default-verified-token-ttl-ms)
        max-entries (or verified-token-max-entries
                        default-verified-token-max-entries)]
    (->KeycloakIdentityProvider
     {:base-url base-url
      :realm realm
      :admin-client-id admin-client-id
      :admin-client-secret admin-client-secret
      :admin-client-private-key-file admin-client-private-key-file
      :expected-issuer expected-issuer}
     (atom nil)
     (atom nil)
     (when (and (pos? ttl-ms) (pos? max-entries))
       (cache/create ttl-ms max-entries)))))
