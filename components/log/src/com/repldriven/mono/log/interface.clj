(ns com.repldriven.mono.log.interface
  (:require
    [com.repldriven.mono.log.core :as core]))

(defmacro debug [& args] `(core/debug ~@args))

(defmacro debugf [& args] `(core/debugf ~@args))

(defmacro info [& args] `(core/info ~@args))

(defmacro infof [& args] `(core/infof ~@args))

(defmacro warn [& args] `(core/warn ~@args))

(defmacro warnf [& args] `(core/warnf ~@args))

(defmacro error [& args] `(core/error ~@args))

(defmacro errorf [& args] `(core/errorf ~@args))

(defn anomaly [& args] (apply core/anomaly args))
