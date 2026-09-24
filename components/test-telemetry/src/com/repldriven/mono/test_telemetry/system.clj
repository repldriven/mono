(ns com.repldriven.mono.test-telemetry.system
  (:require
    [com.repldriven.mono.test-telemetry.hub :as hub]

    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]
    [steffan-westcott.clj-otel.sdk.otel-sdk :as sdk])
  (:import
    (io.opentelemetry.sdk.testing.exporter InMemorySpanExporter)
    (io.opentelemetry.sdk.trace.export SimpleSpanProcessor)))

(defn- in-memory-sdk
  [{:keys [service-name]}]
  (let [exporter (InMemorySpanExporter/create)]
    (log/info "Starting in-memory OpenTelemetry SDK" :service-name service-name)
    ;; SimpleSpanProcessor, not the batching form clj-otel's map syntax
    ;; builds: a span must be readable the moment it closes, with no flush
    ;; interval to wait out. The SDK is this instance's own and never the
    ;; default: the hub stays the default, and hands this exporter every
    ;; span the default tracer closes.
    (let [otel-sdk (sdk/init-otel-sdk! service-name
                                       {:set-as-default false
                                        :register-shutdown-hook false
                                        :tracer-provider
                                        {:span-processors
                                         [(SimpleSpanProcessor/create
                                           exporter)]}})]
      (hub/install)
      (hub/subscribe exporter)
      {:sdk otel-sdk :exporter exporter})))

;; Name this component-kind where a config names `telemetry/otel-sdk` and
;; spans collect in memory instead of shipping over OTLP. The instance shape
;; is the same, so `endpoint` is simply not a key here.
(def otel-sdk
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (in-memory-sdk config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (when instance
                    (log/info "Stopping in-memory OpenTelemetry SDK")
                    (let [^InMemorySpanExporter exporter (:exporter instance)]
                      (hub/unsubscribe exporter)
                      (sdk/close-otel-sdk! (:sdk instance))
                      (.close exporter)
                      ;; A dev system restarted in a REPL must not inherit
                      ;; the spans of the run before it.
                      (.reset exporter))))
   :system/config {:service-name system/required-component}
   :system/config-schema [:map [:service-name string?]]
   :system/instance-schema [:map [:sdk some?] [:exporter some?]]})

(system/defcomponents :test-telemetry {:otel-sdk otel-sdk})
