(ns com.repldriven.mono.test-telemetry.span-tests
  (:require
    [steffan-westcott.clj-otel.api.trace.span :as span]
    [steffan-westcott.clj-otel.sdk.otel-sdk :as sdk]
    [clojure.test :refer [is]])
  (:import
    (io.opentelemetry.api GlobalOpenTelemetry)
    (io.opentelemetry.api.trace Span)
    (io.opentelemetry.sdk.testing.exporter InMemorySpanExporter)
    (io.opentelemetry.sdk.trace.data SpanData)
    (io.opentelemetry.sdk.trace.export SimpleSpanProcessor)))

(defonce ^InMemorySpanExporter shared-exporter (InMemorySpanExporter/create))

(defonce sdk-lock (Object.))

(defn install-sdk!
  "Resets any existing global OTel SDK and installs the test
  SDK with the shared in-memory exporter. Must be called
  under sdk-lock."
  []
  (GlobalOpenTelemetry/resetForTest)
  (let [otel-sdk (sdk/init-otel-sdk! "test"
                                     {:register-shutdown-hook false
                                      :tracer-provider
                                      {:span-processors
                                       [(SimpleSpanProcessor/create
                                         shared-exporter)]}})]
    (span/set-default-tracer! (span/get-tracer {:open-telemetry otel-sdk}))))

(def ^:private await-tries 100)
(def ^:private await-interval-ms 20)

(defn- spans-by-name
  [trace-id]
  (into {}
        (comp (filter (fn [^SpanData s]
                        (= trace-id (.getTraceId (.getSpanContext s)))))
              (map (fn [^SpanData s] [(.getName s) s])))
        (.getFinishedSpanItems shared-exporter)))

(defn await-spans
  "Spans for `trace-id`, by name, once every name in
  `expected-names` has one — or the tries run out, so a genuinely
  missing span still fails its assertion rather than hanging.

  A span reaches the exporter when it CLOSES, and a caller can be
  unblocked from inside one: `command/process` sends its reply
  within the `process-command` span, so the test thread resumes
  while the consumer thread is still unwinding it. Reading once
  races that unwind."
  [trace-id expected-names]
  (loop [n 0]
    (let [spans (spans-by-name trace-id)]
      (if (or (every? #(contains? spans %) expected-names) (>= n await-tries))
        spans
        (do (Thread/sleep await-interval-ms) (recur (inc n)))))))

(defmacro with-span-tests
  "Run body under an in-memory OTel SDK, then automatically
  assert:
   - Each name in expected-names has a corresponding finished
     span
   - All expected spans share the same trace ID

  Resets and re-installs the test SDK before each invocation
  to reclaim the global from any system component that may
  have overwritten it. Creates a root span to establish a
  trace ID, then collects spans carrying that trace ID, waiting
  for ones closed on another thread.

  spans-sym is bound to a map of span-name -> SpanData after
  the body completes. Use _ if you don't need to inspect
  individual spans.

  Usage:
    (with-span-tests [_ [\"process-command\"]]
      (do-work))"
  [[spans-sym expected-names] & body]
  `(let [trace-id# (atom nil)]
     (locking sdk-lock (install-sdk!))
     (span/with-span! ["test-root" {}]
                      (reset! trace-id# (.getTraceId (.getSpanContext
                                                      (Span/current))))
                      ~@body)
     (let [~spans-sym (await-spans @trace-id# ~expected-names)]
       (doseq [n# ~expected-names]
         (is (some? (get ~spans-sym n#)) (str "Should have span named: " n#)))
       (let [trace-ids# (into #{}
                              (map (fn [^SpanData s#]
                                     (.getTraceId (.getSpanContext s#))))
                              (vals ~spans-sym))]
         (is (= 1 (count trace-ids#))
             (str "Expected spans should share one trace ID, got: "
                  trace-ids#))))))
