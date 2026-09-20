(ns com.repldriven.mono.message-bus.core
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.message-bus.protocol :as proto]))

(defrecord Bus [producers consumers])

(defn send
  ([bus producer-name message]
   (proto/send (get (:producers bus) producer-name) message))
  ([bus producer-name message opts]
   (proto/send (get (:producers bus) producer-name) message opts)))

(defn subscribe
  [bus consumer-name handler-fn]
  (proto/subscribe (get (:consumers bus) consumer-name) handler-fn))

(defn unsubscribe
  ([bus consumer-name]
   (proto/unsubscribe (get (:consumers bus) consumer-name)))
  ([bus consumer-name subscription]
   (proto/unsubscribe (get (:consumers bus) consumer-name) subscription)))
