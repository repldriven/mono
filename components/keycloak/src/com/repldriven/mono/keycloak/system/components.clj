(ns com.repldriven.mono.keycloak.system.components
  (:require
    [com.repldriven.mono.keycloak.identity-provider :as kc-idp]
    [com.repldriven.mono.system.interface :as system]))

(def identity-provider
  "Keycloak-backed `IdentityProvider`. Talks to a live Keycloak realm
  over HTTP — pair `:base-url` with whatever exposes the realm (a
  testcontainer for tests, an in-cluster Service URL for prod).
  Config:
  - `:base-url` — Keycloak base URL (no trailing `/realms/…`).
  - `:realm` — realm name.
  - `:admin-client-id` — the service-account client this brick uses to
    call Admin REST.
  - `:admin-client-secret` — that client's secret, for `client_secret`
    authentication.
  - `:admin-client-private-key-file` — path to an RSA private key in
    PEM form, for `private_key_jwt` authentication. Takes precedence
    over `:admin-client-secret` when both are set. A path rather than
    the PEM itself because a multi-line PEM does not survive an
    environment variable intact, and because rotation is then a file
    change rather than a restart.
  - `:expected-issuer` — optional, and required in practice whenever
    `:base-url` is an internal Service URL. Names the realm as Keycloak
    itself sees it: `<public-hostname>/realms/<realm>`. Defaults to
    `<base-url>/realms/<realm>`.
  - `:verified-token-ttl-ms` — optional. How long a token whose
    signature and issuer were verified is kept, never past its `exp`.
    Defaults to a minute; zero verifies every token afresh.
  - `:verified-token-max-entries` — optional. How many verified tokens
    are kept at once. Defaults to ten thousand.

    It governs both directions. Inbound, it is the iss claim the token
    verifier expects. Outbound, it is the audience a `private_key_jwt`
    assertion claims — Keycloak validates that against its own frontend
    URL, never against the address the request arrived on, so an
    assertion built from an internal `:base-url` is refused with
    `invalid_client` however the request is routed.

  Exactly one of `:admin-client-secret` or
  `:admin-client-private-key-file` is required — neither can default,
  because a defaulted credential is one everybody shares."
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (kc-idp/->client config)))
   :system/config {:base-url system/required-component
                   :realm system/required-component
                   :admin-client-id system/required-component
                   :admin-client-secret nil
                   :admin-client-private-key-file nil}
   :system/config-schema
   [:and
    [:map
     [:base-url string?]
     [:realm string?]
     [:admin-client-id string?]
     [:admin-client-secret {:optional true} [:maybe string?]]
     [:admin-client-private-key-file {:optional true} [:maybe string?]]
     [:verified-token-ttl-ms {:optional true} [:maybe nat-int?]]
     [:verified-token-max-entries {:optional true} [:maybe nat-int?]]]
    [:fn
     {:error/message
      (str "one of :admin-client-secret or :admin-client-private-key-file"
           " is required")}
     (fn [config]
       (let [{:keys [admin-client-secret admin-client-private-key-file]}
             config]
         (boolean (or admin-client-secret admin-client-private-key-file))))]]
   :system/instance-schema some?})
