(ns com.repldriven.mono.smtp.system
  (:require
    [com.repldriven.mono.smtp.core :as core]

    [com.repldriven.mono.system.interface :as system]))

(def client
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (core/client config)))
   :system/stop (fn [_] nil)
   :system/config {:host system/required-component
                   :port 587
                   :security :starttls
                   :username nil
                   :password nil
                   :from nil
                   :connection-timeout-ms 10000
                   :timeout-ms 10000
                   :properties {}}
   :system/config-schema
   [:map
    [:host string?]
    [:port int?]
    [:security [:enum :starttls :tls :none]]
    [:username {:optional true} [:maybe string?]]
    [:password {:optional true} [:maybe string?]]
    [:from {:optional true}
     [:maybe
      [:or string? [:map [:address string?] [:name {:optional true} string?]]]]]
    [:connection-timeout-ms int?]
    [:timeout-ms int?]
    [:properties [:map-of [:or keyword? string?] any?]]]
   :system/instance-schema [:map [:session [:fn core/session?]]]})

(system/defcomponents :smtp {:client client})
