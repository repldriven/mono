(ns com.repldriven.mono.identity-provider.system.components
  (:require
    [com.repldriven.mono.identity-provider.local :as local]))

(def local-client
  "In-memory `LocalIdentityProvider` — fast, no external dependencies.
  Config:
  - `:issuer` — string baked into the JWT `iss` claim (and verified
    on roundtrip). Default `https://local.invalid/`."
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (local/->client config)))
   :system/config {:issuer "https://local.invalid/"}
   :system/instance-schema some?})
