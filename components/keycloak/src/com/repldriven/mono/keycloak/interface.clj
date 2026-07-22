(ns com.repldriven.mono.keycloak.interface
  "Public face of the `keycloak` SDK adapter. Loads
  `keycloak.system.core` for side effects (so requiring this
  namespace registers the `:keycloak/identity-provider` system
  kind) but doesn't expose any Keycloak-specific surface — callers
  consume the bound instance via the `identity-provider` brick's
  protocol, never directly through this brick."
  (:require
    com.repldriven.mono.keycloak.system.core))
