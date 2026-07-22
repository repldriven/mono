(ns com.repldriven.mono.log.core
  (:require
    [clojure.tools.logging :as log]
    [com.repldriven.mono.error.interface :as error])
  (:import
    (org.slf4j.bridge SLF4JBridgeHandler)))

;; Install JUL→SLF4J bridge at namespace load so library log
;; records (e.g. from FDB, Pulsar) reach the same backend.
(when-not *compile-files*
  (SLF4JBridgeHandler/removeHandlersForRootLogger)
  (SLF4JBridgeHandler/install))

(defmacro debug [& args] `(log/debug ~@args))

(defmacro debugf [& args] `(log/debugf ~@args))

(defmacro info [& args] `(log/info ~@args))

(defmacro infof [& args] `(log/infof ~@args))

(defmacro warn [& args] `(log/warn ~@args))

(defmacro warnf [& args] `(log/warnf ~@args))

(defmacro error [& args] `(log/error ~@args))

(defmacro errorf [& args] `(log/errorf ~@args))

(defn anomaly
  ([anom-or-opts]
   (if (error/anomaly? anom-or-opts)
     (anomaly anom-or-opts {})
     (fn [anom] (anomaly anom anom-or-opts))))
  ([anom opts]
   (let [{:keys [level message] :or {level :error}} opts
         msg (if message (str message " - " (pr-str anom)) (pr-str anom))]
     (log/log level msg))))
