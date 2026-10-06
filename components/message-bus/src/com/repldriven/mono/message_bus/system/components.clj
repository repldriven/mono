(ns com.repldriven.mono.message-bus.system.components
  (:require
    [com.repldriven.mono.message-bus.core :as core]
    [com.repldriven.mono.message-bus.local :as local]
    [com.repldriven.mono.message-bus.performers :as performers]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]

    [clojure.core.async :as async]))

(defn- max-in-flight-or-throw
  [channel performers max-in-flight]
  (let [limiter (performers/subscription-limiter performers max-in-flight)]
    (when (error/anomaly? limiter)
      ;; nosemgrep: no-raw-throw
      (throw (ex-info (str "Invalid max-in-flight for local channel " channel)
                      {:channel channel :anomaly limiter})))
    max-in-flight))

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
            (into
             {}
             (map
              (fn [[k {:keys [mult]}]]
                (let [n (get (:performers config) k 1)]
                  [k
                   (local/->LocalConsumer
                    mult
                    (atom [])
                    n
                    (max-in-flight-or-throw k n (get (:max-in-flight config) k))
                    (name k))]))
              channels))))))
   ;; `performers` maps a channel to its number of performers; a channel
   ;; it does not name has one. `max-in-flight` maps a channel to its
   ;; limit; a channel it does not name has none.
   :system/config
   {:channels system/required-component :performers nil :max-in-flight nil}
   :system/instance-schema some?})
