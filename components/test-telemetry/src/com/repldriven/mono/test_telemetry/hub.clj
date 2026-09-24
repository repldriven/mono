(ns com.repldriven.mono.test-telemetry.hub
  (:require
    [steffan-westcott.clj-otel.api.otel :as otel]
    [steffan-westcott.clj-otel.api.trace.span :as span]
    [steffan-westcott.clj-otel.sdk.otel-sdk :as sdk])
  (:import
    (io.opentelemetry.api.trace Tracer)
    (io.opentelemetry.sdk.common CompletableResultCode)
    (io.opentelemetry.sdk.trace.export SimpleSpanProcessor SpanExporter)
    (java.util.concurrent.locks ReentrantReadWriteLock)))

(defonce ^:private subscribers (atom #{}))

(defn- fan-out-exporter
  ^SpanExporter []
  (reify
   SpanExporter
     (export [_ spans]
       (doseq [^SpanExporter exporter @subscribers] (.export exporter spans))
       (CompletableResultCode/ofSuccess))
     (flush [_] (CompletableResultCode/ofSuccess))
     (shutdown [_] (CompletableResultCode/ofSuccess))))

(defonce ^:private hub-sdk
  (delay (sdk/init-otel-sdk! "test"
                             {:set-as-default false
                              :register-shutdown-hook false
                              :tracer-provider {:span-processors
                                                [(SimpleSpanProcessor/create
                                                  (fan-out-exporter))]}})))

(defonce ^:private hub-tracer
  (delay (span/get-tracer {:open-telemetry @hub-sdk})))

(defonce ^:private ^ReentrantReadWriteLock defaults-lock
  (ReentrantReadWriteLock.))

(defn tracer ^Tracer [] @hub-tracer)

(defn install
  []
  (otel/set-default-otel! @hub-sdk)
  (span/set-default-tracer! @hub-tracer))

(defn subscribe [exporter] (swap! subscribers conj exporter))

(defn unsubscribe [exporter] (swap! subscribers disj exporter))

(defn with-defaults-held
  [f]
  (let [lock (.readLock defaults-lock)]
    (.lock lock)
    (try (install) (f) (finally (.unlock lock)))))

(defn with-defaults-taken
  [f]
  (let [lock (.writeLock defaults-lock)]
    (.lock lock)
    (try (f) (finally (install) (.unlock lock)))))
