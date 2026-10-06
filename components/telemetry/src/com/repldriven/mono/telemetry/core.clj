(ns com.repldriven.mono.telemetry.core
  "Telemetry abstraction layer wrapping OpenTelemetry.

  Provides tracing and metrics without direct clj-otel coupling in domain code."
  (:require
    [steffan-westcott.clj-otel.api.trace.span :as span]
    [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
    [steffan-westcott.clj-otel.api.otel :as otel]
    [steffan-westcott.clj-otel.context :as context])
  (:import
    (io.opentelemetry.api.trace Span SpanContext)
    (io.opentelemetry.context.propagation TextMapGetter)))

(defn span-opts
  [name-and-attrs source]
  (cond (map? name-and-attrs)
        (update name-and-attrs :source (fn [s] (merge source s)))

        (vector? name-and-attrs)
        (let [[span-name attrs] name-and-attrs]
          {:name span-name :attributes attrs :source source})

        :else
        {:name name-and-attrs :source source}))

(defn in-span
  [opts f]
  (let [ctx (span/new-span!' opts)]
    (try (context/with-context! ctx (f))
         (catch Throwable e
           (span/add-exception! e {:context ctx})
           ;; nosemgrep: no-raw-throw — the caller's exception, recorded
           (throw e))
         (finally (span/end-span! {:context ctx})))))

;; Degrading gracefully means the body runs exactly once whatever OpenTelemetry
;; does, and that the caller's own exceptions reach the caller unchanged. A
;; plain (catch Exception _ body) cannot tell the span machinery failing from
;; the body failing, so it re-ran a body that had already thrown — charging the
;; card twice, and swallowing the first exception. These three volatiles are
;; how we tell the cases apart.
(defmacro with-span
  "Add a span around code execution.

  Usage:
    (with-span [\"operation-name\" {:attr/key \"value\"}]
      (do-work))

  Falls back gracefully if OpenTelemetry is not configured: the body
  still runs, exactly once. An exception from the body is the
  caller's and propagates."
  [name-and-attrs & body]
  (let [{:keys [line column]} (meta &form)
        source {:file *file* :line line :col column}]
    `(let [started?# (volatile! false)
           finished?# (volatile! false)
           result# (volatile! nil)]
       (try (in-span (span-opts ~name-and-attrs ~source)
                     (fn []
                       (vreset! started?# true)
                       (let [r# (do ~@body)]
                         (vreset! finished?# true)
                         (vreset! result# r#)
                         r#)))
            (catch Exception e#
              (cond
               ;; span creation failed, so the body never ran: run it now
               (not @started?#)
               (do ~@body)

               ;; the body finished and only closing the span failed:
               ;; telemetry must not lose a result the caller already
               ;; computed
               @finished?#
               @result#

               ;; the body itself threw: that is the caller's exception
               :else
               ;; nosemgrep: no-raw-throw — the caller's exception
               (throw e#)))))))

(defn with-span-parent
  "Create a span with an explicit parent context.

  Args:
  - name: Span name
  - parent-ctx: Parent OpenTelemetry context (from extract-parent-context)
  - attrs: Span attributes map
  - f: Function to execute within the span

  Returns: Result of executing f, which runs exactly once. An
  exception from f is the caller's and propagates."
  [name parent-ctx attrs f]
  (let [started? (volatile! false)
        finished? (volatile! false)
        result (volatile! nil)]
    (try (in-span {:name name
                   :parent parent-ctx
                   :span-kind :consumer
                   :attributes attrs}
                  (fn []
                    (vreset! started? true)
                    (let [r (f)]
                      (vreset! finished? true)
                      (vreset! result r)
                      r)))
         (catch Exception e
           ;; See the note on with-span: f runs once, and its own failures
           ;; belong to the caller.
           (cond (not @started?)
                 (f)

                 @finished?
                 @result

                 :else
                 ;; nosemgrep: no-raw-throw — the caller's exception
                 (throw e))))))

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

(defn set-error
  [description]
  (try (span/add-span-data! {:status {:code :error
                                      :description description}})
       (catch Exception _e
         nil)))

(defn counter
  "Create or get a counter instrument.

  Options:
    :name - Instrument name (required)
    :description - Human-readable description
    :unit - Unit of measurement
    :otel - a `telemetry/otel-sdk` instance whose meter creates it

  Returns nil if OpenTelemetry is not configured, which the counter
  functions below treat as a no-op."
  [opts]
  (let [{:keys [otel]} opts
        sdk (:sdk otel)]
    (when (or sdk (not (contains? opts :otel)))
      (try (instrument/instrument
            (cond-> (assoc (dissoc opts :otel) :instrument-type :counter)
                    sdk
                    (assoc :meter
                           (instrument/get-meter {:open-telemetry sdk}))))
           (catch Exception _e nil)))))

(defn inc-counter!
  "Increment a counter with attributes.

  Usage:
    (inc-counter! my-counter {:reason :validation-failed})

  No-op if OpenTelemetry is not configured."
  [counter attrs]
  (when counter
    (try (instrument/add! counter {:value 1 :attributes attrs})
         (catch Exception _e nil))))

(defn add-counter!
  "Add a value to a counter with attributes.

  Usage:
    (add-counter! my-counter 5 {:operation :batch-insert})

  No-op if OpenTelemetry is not configured."
  [counter value attrs]
  (when counter
    (try (instrument/add! counter {:value value :attributes attrs})
         (catch Exception _e nil))))

(defn- measurements
  [observed]
  (cond
   (number? observed)
   {:value (double observed)}

   (nil? observed)
   nil

   :else
   (map (fn [m] (update m :value double)) observed)))

(defn gauge
  [opts]
  (let [{:keys [otel observe]} opts
        sdk (:sdk otel)]
    (when (or sdk (not (contains? opts :otel)))
      (try (instrument/instrument
            (cond-> (-> opts
                        (dissoc :otel :observe)
                        (assoc :instrument-type :gauge
                               :measurement-type :double))
                    sdk
                    (assoc :meter
                           (instrument/get-meter {:open-telemetry sdk})))
            (fn [] (measurements (observe))))
           (catch Exception _e nil)))))

(defn close-instrument
  [instrument]
  (when instrument
    (try (.close ^java.lang.AutoCloseable instrument)
         (catch Exception _e nil))))

(defn- traceparent-from-span-context
  [^SpanContext span-context]
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
