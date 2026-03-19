(ns com.repldriven.mono.message-bus.interface
  (:refer-clojure :exclude [send])
  (:require
    com.repldriven.mono.message-bus.system.core
    [com.repldriven.mono.message-bus.core :as core]
    [com.repldriven.mono.message-bus.protocol :as protocol]))

;; Protocols are part of the public interface. Components that implement
;; message-bus producers or consumers require this namespace and extend
;; Producer/Consumer.
(def Producer protocol/Producer)
(def Consumer protocol/Consumer)

(defn send [bus producer-name message] (core/send bus producer-name message))

(defn subscribe
  [bus consumer-name handler-fn]
  (core/subscribe bus consumer-name handler-fn))

(defn unsubscribe [bus consumer-name] (core/unsubscribe bus consumer-name))
