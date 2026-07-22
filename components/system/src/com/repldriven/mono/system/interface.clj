(ns com.repldriven.mono.system.interface
  "donut.system facade. Builds system definitions from EDN/YAML
  config, registers component-kinds via `defcomponents`, and
  starts/stops the resulting system. Anomalies returned from any
  component lifecycle fn are surfaced as system-start failures
  rather than persisted as instances."
  (:require
    com.repldriven.mono.system.reader.edn
    com.repldriven.mono.system.reader.yml
    [com.repldriven.mono.system.core :as core]
    [com.repldriven.mono.system.components :as components]))

(defn system?
  "True if `config-name` resolves to a valid donut.system map.

  Args:
  - config-name: keyword or path to the system config."
  [config-name]
  (core/system? config-name))

(defn start
  "Build defs from `config-name` and start the donut.system.
  Returns the started system, or an anomaly.

  Args:
  - config-name: classpath/file ref to the EDN/YAML config.
  - custom-config: optional overrides merged into the parsed config.
  - component-ids: optional subset of component ids to start."
  ([config-name] (core/start config-name))
  ([config-name custom-config] (core/start config-name custom-config))
  ([config-name custom-config component-ids]
   (core/start config-name custom-config component-ids)))

(defn instance
  "Look up a started component instance by `[group component]`
  keyword path.

  Args:
  - system: a started system map.
  - kws: keyword vector path under the system's instances."
  [system kws]
  (core/instance system kws))

(defn config
  "Return the resolved config for a given component.

  Args:
  - system: a started system map.
  - group: component group keyword.
  - component: component keyword within the group."
  [system group component]
  (core/config system group component))

(defn stop
  "Stop the system, returning nil or an anomaly.

  Args:
  - system: a started system map."
  [system]
  (core/stop system))

(def
  ^{:doc
    "Sentinel placed in a component's :system/config to mark
  a value as a required dependency on another component."}
  required-component
  core/required-component)

(defmacro defcomponents
  "Register a `component-kind` defmethod for each entry in
  `component-map` under the `ns-keyword` namespace.

  Usage:
    (defcomponents :server
      {:interceptors components/interceptors
       :jetty-adapter components/jetty-adapter})"
  [ns-keyword component-map]
  `(components/defcomponents ~ns-keyword ~component-map))

(defn defs
  "Build system definitions from a parsed config map. The optional
  `ks` path defaults to `[:system]`.

  Args:
  - config: parsed config map (output of env/config).
  - ks: optional vector path to the system block."
  ([config] (components/defs config))
  ([config ks] (components/defs config ks)))

(defmacro with-system
  "Bind `sys-binding` to a started system for the body's duration,
  stopping it on exit. No-op stop if the system value is itself an
  anomaly."
  {:clj-kondo/lint-as 'clojure.core/let}
  [sys-binding & body]
  `(core/with-system ~sys-binding ~@body))
