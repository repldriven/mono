(ns com.repldriven.mono.telemetry.system
  (:require
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]
    [clojure.string :as string]
    [steffan-westcott.clj-otel.api.trace.span :as span]
    [steffan-westcott.clj-otel.sdk.otel-sdk :as sdk])
  (:import
    (io.opentelemetry.exporter.otlp.http.trace OtlpHttpSpanExporter)))

(defn- span-exporter
  [{:keys [endpoint]}]
  (cond-> (OtlpHttpSpanExporter/builder)
          (not (string/blank? endpoint))
          (.setEndpoint endpoint)
          true
          (.build)))

(def otel-sdk
  {:system/start
   (fn [{:system/keys [config instance]}]
     (if instance
       instance
       (let [{:keys [service-name endpoint]} config]
         (if (string/blank? endpoint)
           (do (log/info "OpenTelemetry SDK disabled — no endpoint configured")
               nil)
           (let [exporter (span-exporter config)]
             (log/info "Starting OpenTelemetry SDK"
                       :service-name service-name
                       :endpoint endpoint)
             (let [otel-sdk (sdk/init-otel-sdk! service-name
                                                {:register-shutdown-hook false
                                                 :tracer-provider
                                                 {:span-processors
                                                  [{:exporters [exporter]}]}})]
               (span/set-default-tracer! (span/get-tracer))
               {:sdk otel-sdk :exporter exporter}))))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when instance
                    (log/info "Stopping OpenTelemetry SDK")
                    (sdk/close-otel-sdk! (:sdk instance))
                    (.close (:exporter instance))))
   :system/config {:service-name system/required-component}
   :system/config-schema [:map [:service-name string?]
                          [:endpoint {:optional true} string?]]
   :system/instance-schema [:maybe map?]})

(system/defcomponents :telemetry {:otel-sdk otel-sdk})
