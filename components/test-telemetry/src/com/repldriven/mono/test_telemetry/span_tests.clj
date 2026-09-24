(ns com.repldriven.mono.test-telemetry.span-tests
  (:require
    [com.repldriven.mono.test-telemetry.hub :as hub]

    [steffan-westcott.clj-otel.api.trace.span :as span]
    [clojure.test :refer [is]])
  (:import
    (io.opentelemetry.api.trace Span)
    (io.opentelemetry.sdk.testing.exporter InMemorySpanExporter)
    (io.opentelemetry.sdk.trace.data SpanData)))

(def ^:private await-tries 100)
(def ^:private await-interval-ms 20)

(defn- spans-by-name
  [^InMemorySpanExporter exporter trace-id]
  (into {}
        (comp (filter (fn [^SpanData s]
                        (= trace-id (.getTraceId (.getSpanContext s)))))
              (map (fn [^SpanData s] [(.getName s) s])))
        (.getFinishedSpanItems exporter)))

(defn await-spans
  "Spans for `trace-id`, by name, once every name in
  `expected-names` has one — or the tries run out, so a genuinely
  missing span still fails its assertion rather than hanging.

  A span reaches the exporter when it CLOSES, and a caller can be
  unblocked from inside one: `command/process` sends its reply
  within the `process-command` span, so the test thread resumes
  while the consumer thread is still unwinding it. Reading once
  races that unwind."
  [exporter trace-id expected-names]
  (loop [n 0]
    (let [spans (spans-by-name exporter trace-id)]
      (if (or (every? #(contains? spans %) expected-names) (>= n await-tries))
        spans
        (do (Thread/sleep (long await-interval-ms)) (recur (inc n)))))))

(defmacro with-span-tests
  "Run body with the in-memory hub as the default SDK, then
  automatically assert:
   - Each name in expected-names has a corresponding finished
     span
   - All expected spans share the same trace ID

  Holds the hub as the default for the whole body, so a telemetry
  component another test starts meanwhile cannot take the spans.
  Creates a root span to establish a trace ID, then collects spans
  carrying that trace ID, waiting for ones closed on another
  thread.

  spans-sym is bound to a map of span-name -> SpanData after
  the body completes. Use _ if you don't need to inspect
  individual spans.

  Usage:
    (with-span-tests [_ [\"process-command\"]]
      (do-work))"
  [[spans-sym expected-names] & body]
  `(let [trace-id# (atom nil)
         exporter# (InMemorySpanExporter/create)]
     (hub/subscribe exporter#)
     (try
       (hub/with-defaults-held
        (fn []
          (span/with-span! {:name "test-root" :tracer (hub/tracer)}
                           (reset! trace-id# (.getTraceId (.getSpanContext
                                                           (Span/current))))
                           ~@body)
          (let [~spans-sym (await-spans exporter# @trace-id# ~expected-names)]
            (doseq [n# ~expected-names]
              (is (some? (get ~spans-sym n#))
                  (str "Should have span named: " n#)))
            (let [trace-ids# (into #{}
                                   (map (fn [^SpanData s#]
                                          (.getTraceId (.getSpanContext s#))))
                                   (vals ~spans-sym))]
              (is (= 1 (count trace-ids#))
                  (str "Expected spans should share one trace ID, got: "
                       trace-ids#))))))
       (finally (hub/unsubscribe exporter#)))))
