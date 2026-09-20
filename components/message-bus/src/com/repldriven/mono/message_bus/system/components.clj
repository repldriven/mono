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
         ;; A mult per channel, so every subscriber taps its own copy.
         ;; Taking straight from the channel would make subscribers
         ;; compete for messages, and a broker gives each its own.
         (let [channels (into {}
                              (map (fn [name]
                                     (let [ch (async/chan 10)]
                                       [(keyword name)
                                        {:ch ch :mult (async/mult ch)}]))
                                   (:channels config)))]
           (core/->Bus
            (into {}
                  (map (fn [[k {:keys [ch]}]] [k (local/->LocalProducer ch)])
                       channels))
            (into {}
                  (map (fn [[k {:keys [mult]}]]
                         [k (local/->LocalConsumer mult (atom []))])
                       channels))))))
   :system/config {:channels system/required-component}
   :system/instance-schema some?})
