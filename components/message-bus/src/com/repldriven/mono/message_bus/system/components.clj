(ns com.repldriven.mono.message-bus.system.components
  (:require
    [com.repldriven.mono.message-bus.core :as core]
    [com.repldriven.mono.message-bus.local :as local]
    [com.repldriven.mono.system.interface :as system]
    [clojure.core.async :as async]))

(def bus
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (core/->Bus (:producers config) (:consumers config))))
   :system/config {:producers system/required-component
                   :consumers system/required-component}
   :system/config-schema [:map [:producers map?] [:consumers map?]]
   :system/instance-schema some?})

(def local-bus
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [channels (into {}
                              (map #(vector (keyword %) (async/chan 10))
                                   (:channels config)))]
           (core/->Bus
            (into {}
                  (map (fn [[k ch]] [k (local/->LocalProducer ch)]) channels))
            (into {}
                  (map (fn [[k ch]] [k (local/->LocalConsumer ch (atom nil))])
                       channels))))))
   :system/config {:channels system/required-component}
   :system/instance-schema some?})
