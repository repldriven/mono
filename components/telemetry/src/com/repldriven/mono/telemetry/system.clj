(ns com.repldriven.mono.telemetry.system
  (:require
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]
    [clojure.string :as string]
    [steffan-westcott.clj-otel.api.trace.span :as span]
    [steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java17 :as
     runtime-telemetry]
    [steffan-westcott.clj-otel.sdk.meter-provider :as meter]
    [steffan-westcott.clj-otel.sdk.otel-sdk :as sdk])
  (:import
    (io.opentelemetry.exporter.otlp.http.metrics OtlpHttpMetricExporter)
    (io.opentelemetry.exporter.otlp.http.trace OtlpHttpSpanExporter)
    (java.time Duration)))

(defn- span-exporter
  [{:keys [endpoint]}]
  (cond-> (OtlpHttpSpanExporter/builder)
          (not (string/blank? endpoint))
          (.setEndpoint endpoint)

          true
          (.build)))

(def
  ^{:private true
    :doc
    "How often the JVM's metrics are exported, where the config
  names no `metrics-interval-ms`."}
  default-metrics-interval-ms
  10000)

(defn- metric-exporter
  [{:keys [metrics-endpoint]}]
  (when-not (string/blank? metrics-endpoint)
    (-> (OtlpHttpMetricExporter/builder)
        (.setEndpoint metrics-endpoint)
        (.build))))

(defn- meter-provider
  [exporter {:keys [metrics-interval-ms]}]
  {:readers [{:metric-reader (meter/periodic-metric-reader
                              {:metric-exporter exporter
                               :interval (Duration/ofMillis
                                          (or
                                           metrics-interval-ms
                                           default-metrics-interval-ms))})}]})

;; OTLP over HTTP is the only exporter here, so that no service carries
;; opentelemetry-sdk-testing on its runtime classpath. For spans collected in
;; memory, name `test-telemetry/otel-sdk` in place of this component-kind.
(def otel-sdk
  {:system/start
   (fn [{:system/keys [config instance]}]
     (if instance
       instance
       (let [{:keys [service-name endpoint]} config]
         (if (string/blank? endpoint)
           (do (log/info "OpenTelemetry SDK disabled — no endpoint configured")
               nil)
           (let [exporter (span-exporter config)
                 metrics (metric-exporter config)]
             (log/info "Starting OpenTelemetry SDK"
                       :service-name service-name
                       :endpoint endpoint
                       :metrics-endpoint (:metrics-endpoint config))
             (let [otel-sdk (sdk/init-otel-sdk!
                             service-name
                             (cond-> {:register-shutdown-hook false
                                      :tracer-provider
                                      {:span-processors
                                       [{:exporters [exporter]}]}}
                                     metrics
                                     (assoc :meter-provider
                                            (meter-provider metrics config))))]
               (span/set-default-tracer! (span/get-tracer {:open-telemetry
                                                           otel-sdk}))
               (cond-> {:sdk otel-sdk :exporter exporter}
                       metrics
                       (assoc :metric-exporter metrics
                              :runtime-metrics (runtime-telemetry/register!
                                                otel-sdk
                                                {:jfr false})))))))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when instance
                    (log/info "Stopping OpenTelemetry SDK")
                    (some-> (:runtime-metrics instance)
                            runtime-telemetry/close!)
                    (sdk/close-otel-sdk! (:sdk instance))
                    (.close (:exporter instance))))
   :system/config {:service-name system/required-component}
   :system/config-schema [:map [:service-name string?]
                          [:endpoint {:optional true} string?]
                          [:metrics-endpoint {:optional true} string?]
                          [:metrics-interval-ms {:optional true} pos-int?]]
   :system/instance-schema [:maybe map?]})

(system/defcomponents :telemetry {:otel-sdk otel-sdk})
