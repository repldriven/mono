(ns com.repldriven.mono.test-telemetry.interface
  "In-memory OpenTelemetry, for tests and for a dev system whose
  spans you want to read in the REPL rather than ship to a
  collector.

  Registers the `test-telemetry/otel-sdk` component-kind: name it
  where a config names `telemetry/otel-sdk` and spans collect in
  memory, readable through `finished-spans` as soon as they close."
  (:require
    com.repldriven.mono.test-telemetry.system
    [com.repldriven.mono.test-telemetry.core :as core]
    [com.repldriven.mono.test-telemetry.span-tests :as span-tests]))

(defn finished-spans
  "Spans an in-memory telemetry instance has collected so far.

  Takes the `test-telemetry/otel-sdk` system instance. Returns a
  vector of `SpanData`, or nil for any other telemetry instance."
  [instance]
  (core/finished-spans instance))

(defn clear-spans!
  "Discard the spans an in-memory telemetry instance has collected.

  Takes the `test-telemetry/otel-sdk` system instance. No-op for
  any other telemetry instance."
  [instance]
  (core/clear-spans! instance))

(defn tracer
  "The tracer of an in-memory telemetry instance's own SDK.

  Takes the `test-telemetry/otel-sdk` system instance. A span created
  with it lands in that instance's exporter whatever the default tracer
  is at the time. Returns nil for any other telemetry instance."
  [instance]
  (core/tracer instance))

(defmacro with-span-tests
  "Run body under an in-memory OTel SDK, then automatically assert:
   - Each name in expected-names has a corresponding finished span
   - All finished spans share the same trace ID (W3C propagation worked)

   spans-sym is bound to a map of span-name -> SpanData after the body completes.
   Use _ if you don't need to inspect individual spans.

   Usage:
     (with-span-tests [_ [\"process-command\"]]
       (do-work))"
  {:clj-kondo/lint-as 'clojure.core/let}
  [[spans-sym expected-names] & body]
  `(span-tests/with-span-tests [~spans-sym ~expected-names] ~@body))
