(ns com.repldriven.mono.system.interface
  (:require
    com.repldriven.mono.system.reader.edn
    com.repldriven.mono.system.reader.yml
    [com.repldriven.mono.system.core :as core]
    [com.repldriven.mono.system.components :as components]))

(defn system? [config-name] (core/system? config-name))

(defn start
  ([config-name] (core/start config-name))
  ([config-name custom-config] (core/start config-name custom-config))
  ([config-name custom-config component-ids]
   (core/start config-name custom-config component-ids)))

(defn instance [system kws] (core/instance system kws))

(defn config [system group component] (core/config system group component))

(defn stop [system] (core/stop system))

(def required-component core/required-component)

(defmacro defcomponents
  [ns-keyword component-map]
  `(components/defcomponents ~ns-keyword ~component-map))

(defn defs
  ([config] (components/defs config))
  ([config ks] (components/defs config ks)))

(defmacro with-system
  {:clj-kondo/lint-as 'clojure.core/let}
  [sys-binding & body]
  `(core/with-system ~sys-binding ~@body))
