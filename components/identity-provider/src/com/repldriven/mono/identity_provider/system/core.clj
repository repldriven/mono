(ns com.repldriven.mono.identity-provider.system.core
  (:require
    [com.repldriven.mono.identity-provider.system.components :as components]
    [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :identity-provider {:local components/local-client})
