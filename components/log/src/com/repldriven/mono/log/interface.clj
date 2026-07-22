(ns com.repldriven.mono.log.interface
  "Thin wrapper over `clojure.tools.logging` plus an `anomaly`
  helper for rendering anomaly values into log output. The brick
  also installs the JUL→SLF4J bridge so library log records reach
  the same backend."
  (:require
    [com.repldriven.mono.log.core :as core]))

(defmacro debug "Log at DEBUG level." [& args] `(core/debug ~@args))

(defmacro debugf
  "Log at DEBUG level with format-string args."
  [& args]
  `(core/debugf ~@args))

(defmacro info "Log at INFO level." [& args] `(core/info ~@args))

(defmacro infof
  "Log at INFO level with format-string args."
  [& args]
  `(core/infof ~@args))

(defmacro warn "Log at WARN level." [& args] `(core/warn ~@args))

(defmacro warnf
  "Log at WARN level with format-string args."
  [& args]
  `(core/warnf ~@args))

(defmacro error "Log at ERROR level." [& args] `(core/error ~@args))

(defmacro errorf
  "Log at ERROR level with format-string args."
  [& args]
  `(core/errorf ~@args))

(defn anomaly
  "Log an anomaly value. Called with one anomaly logs at ERROR;
  called with one opts map returns a log fn pre-configured with
  those opts; called with both logs the anomaly using the opts.

  Args:
  - anom-or-opts: an anomaly value or an opts map (`:level`, `:message`).
  - opts: optional opts map when called with two args."
  [& args]
  (apply core/anomaly args))
