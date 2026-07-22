(ns com.repldriven.mono.keycloak.system.core
  (:require
    [com.repldriven.mono.keycloak.system.components :as components]
    [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :keycloak
                      {:identity-provider components/identity-provider})
