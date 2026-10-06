(ns com.repldriven.mono.test-telemetry.core
  "Reading spans back off an in-memory telemetry instance."
  (:require
    [steffan-westcott.clj-otel.api.trace.span :as span])
  (:import
    (io.opentelemetry.sdk.metrics.data LongPointData MetricData)
    (io.opentelemetry.sdk.testing.exporter InMemoryMetricReader
                                           InMemorySpanExporter)))

(defn- in-memory-exporter
  ^InMemorySpanExporter [instance]
  (let [exporter (:exporter instance)]
    (when (instance? InMemorySpanExporter exporter) exporter)))

(defn finished-spans
  "Spans an in-memory telemetry instance has collected so far.

  Returns a vector of `SpanData`, or nil when the instance is not
  collecting in memory."
  [instance]
  (when-let [exporter (in-memory-exporter instance)]
    (vec (.getFinishedSpanItems exporter))))

(defn clear-spans!
  "Discard the spans an in-memory telemetry instance has collected.

  No-op when the instance is not collecting in memory."
  [instance]
  (when-let [exporter (in-memory-exporter instance)] (.reset exporter)))

(defn tracer
  "The tracer of an in-memory telemetry instance's own SDK.

  A span created with it lands in this instance's exporter whatever the
  default tracer is at the time, which with namespaces running in
  parallel is whichever telemetry component started last. Returns nil
  when the instance is not collecting in memory."
  [instance]
  (when (in-memory-exporter instance)
    (span/get-tracer {:open-telemetry (:sdk instance)})))

(defn counter-value
  [instance counter-name attributes]
  (let [reader (:metric-reader instance)]
    (when (instance? InMemoryMetricReader reader)
      (->> (.collectAllMetrics ^InMemoryMetricReader reader)
           (filter (fn [^MetricData m] (= counter-name (.getName m))))
           (mapcat (fn [^MetricData m] (.getPoints (.getLongSumData m))))
           (filter (fn [^LongPointData p]
                     (= attributes
                        (into {}
                              (map (fn [[k v]] [(.getKey k) v]))
                              (.asMap (.getAttributes p))))))
           (map (fn [^LongPointData p] (.getValue p)))
           (reduce + 0)))))
