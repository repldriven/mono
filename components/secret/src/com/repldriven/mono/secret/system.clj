(ns com.repldriven.mono.secret.system
  (:require
    [com.repldriven.mono.secret.env :as env]
    [com.repldriven.mono.secret.fixture :as fixture]
    [com.repldriven.mono.secret.gcp :as gcp]
    [com.repldriven.mono.secret.pass :as pass]
    [com.repldriven.mono.system.interface :as system]))

(def pass-provider
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (pass/->PassSecretProvider (:secret-map config))))
   :system/config {:secret-map {}}
   :system/instance-schema some?})

(def gcp-provider
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (gcp/->SecretManagerProvider (:project config)
                                                    (:secret-map config))))
   :system/config {:project system/required-component :secret-map {}}
   :system/instance-schema some?})

(def env-provider
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (env/->EnvSecretProvider (:secret-map config))))
   :system/config {:secret-map {}}
   :system/instance-schema some?})

(def fixture-provider
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (fixture/->FixtureSecretProvider (:secrets config))))
   :system/config {:secrets {}}
   :system/instance-schema some?})

(system/defcomponents :secret
                      {:pass-provider pass-provider
                       :env-provider env-provider
                       :gcp-provider gcp-provider
                       :fixture-provider fixture-provider})
