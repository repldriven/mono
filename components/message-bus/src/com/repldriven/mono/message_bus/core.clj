(ns com.repldriven.mono.message-bus.core
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.message-bus.protocol :as proto]

    [com.repldriven.mono.telemetry.interface :as telemetry]))

(defrecord Bus [producers consumers])

(defn- with-send-span
  [producer-name f]
  (telemetry/with-span {:name "bus-send"
                        :kind :producer
                        :attributes {:messaging.destination.name
                                     (name producer-name)}}
                       (f)))

(defn send
  ([bus producer-name message]
   (with-send-span producer-name
                   #(proto/send (get (:producers bus) producer-name) message)))
  ([bus producer-name message opts]
   (with-send-span producer-name
                   #(proto/send (get (:producers bus) producer-name)
                                message
                                opts))))

(defn subscribe
  [bus consumer-name handler-fn]
  (proto/subscribe (get (:consumers bus) consumer-name) handler-fn))

(defn unsubscribe
  ([bus consumer-name]
   (proto/unsubscribe (get (:consumers bus) consumer-name)))
  ([bus consumer-name subscription]
   (proto/unsubscribe (get (:consumers bus) consumer-name) subscription)))
