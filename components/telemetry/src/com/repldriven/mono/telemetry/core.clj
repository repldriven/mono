(ns com.repldriven.mono.telemetry.core
  "Telemetry abstraction layer wrapping OpenTelemetry.

  Provides tracing and metrics without direct clj-otel coupling in domain code."
  (:require
    [steffan-westcott.clj-otel.api.trace.span :as span]
    [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
    [steffan-westcott.clj-otel.api.otel :as otel]
    [steffan-westcott.clj-otel.context :as context])
  (:import
    (io.opentelemetry.api.trace Span)
    (io.opentelemetry.context.propagation TextMapGetter)))

(defmacro with-span
  "Add a span around code execution.

  Usage:
    (with-span [\"operation-name\" {:attr/key \"value\"}]
      (do-work))

  Falls back gracefully if OpenTelemetry is not configured."
  [name-and-attrs & body]
  `(try (span/with-span! ~name-and-attrs ~@body)
        (catch Exception _e#
          ;; Gracefully degrade if OTel not configured
          (do ~@body))))

(defn with-span-parent
  "Create a span with an explicit parent context.

  Args:
  - name: Span name
  - parent-ctx: Parent OpenTelemetry context (from extract-parent-context)
  - attrs: Span attributes map
  - f: Function to execute within the span

  Returns: Result of executing f"
  [name parent-ctx attrs f]
  (try (span/with-span!
        {:name name :parent parent-ctx :kind :consumer :attributes attrs}
        (f))
       (catch Exception _e
         ;; Gracefully degrade if OTel not configured
         (f))))

(defn add-event
  "Add an event to the current span with attributes.

  No-op if no span is active or OpenTelemetry is not configured."
  [name attrs]
  (try (span/add-event! name attrs)
       (catch Exception _e
         ;; No-op if OTel not configured
         nil)))

(defn set-attribute
  "Set an attribute on the current span.

  No-op if no span is active or OpenTelemetry is not configured."
  [k v]
  (try (span/add-span-data! {:attributes {k v}})
       (catch Exception _e
         ;; No-op if OTel not configured
         nil)))

(defn counter
  "Create or get a counter instrument.

  Options:
    :name - Instrument name (required)
    :description - Human-readable description
    :unit - Unit of measurement"
  [opts]
  (instrument/instrument (assoc opts :instrument-type :counter)))

(defn inc-counter!
  "Increment a counter with attributes.

  Usage:
    (inc-counter! my-counter {:reason :validation-failed})"
  [counter attrs]
  (instrument/add! counter {:value 1 :attributes attrs}))

(defn add-counter!
  "Add a value to a counter with attributes.

  Usage:
    (add-counter! my-counter 5 {:operation :batch-insert})"
  [counter value attrs]
  (instrument/add! counter {:value value :attributes attrs}))

(defn- traceparent-from-span-context
  [span-context]
  (when (.isValid span-context)
    (format "00-%s-%s-%02x"
            (.getTraceId span-context)
            (.getSpanId span-context)
            (if (.isSampled span-context) 1 0))))

(defn inject-traceparent
  "Extract W3C traceparent from the current thread-local span (Span/current).

  Returns traceparent string in format: 00-{trace-id}-{span-id}-{trace-flags}
  Returns nil if no active span or OpenTelemetry is not configured."
  []
  (try (traceparent-from-span-context (.getSpanContext (Span/current)))
       (catch Exception _e nil)))

(def ^:private command-getter
  "TextMapGetter implementation for extracting trace context from command maps."
  (reify
   TextMapGetter
     (keys [_ _carrier] ["traceparent" "tracestate"])
     (get [_ carrier key] (clojure.core/get carrier key))))

(defn extract-parent-context
  "Extract parent OpenTelemetry context from command with traceparent/tracestate.

  Args:
  - command: Map with string keys containing \"traceparent\" and \"tracestate\" fields

  Returns: OpenTelemetry context with extracted trace information, or current context if extraction fails."
  [command]
  (try (let [propagator (.getTextMapPropagator (.getPropagators
                                                (otel/get-default-otel!)))
             carrier {"traceparent" (:traceparent command)
                      "tracestate" (:tracestate command)}]
         (.extract propagator (context/current) carrier command-getter))
       (catch Exception _e
         ;; Fallback to current context if extraction fails
         (context/current))))
