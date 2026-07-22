(ns com.repldriven.mono.telemetry.interceptors
  "Interceptors for distributed tracing."
  (:require
    [steffan-westcott.clj-otel.api.trace.http :as trace-http]))

(defn- pedestal->sieppari-error
  "Adapter for the `:error` interceptor stage. clj-otel's
  interceptors are Pedestal-shaped (`(fn [ctx ex])`); Sieppari
  invokes `:error` with the ctx alone and stores the exception
  under `(:error ctx)`. Without this adapter, the first time
  anything downstream throws Sieppari calls the otel error fn
  with one arg and crashes with `Wrong number of args (1)`. In
  dev nothing throws so the mismatch hides; on a real cluster
  with a live exporter it surfaces."
  [pedestal-error-fn]
  (fn [ctx]
    (pedestal-error-fn ctx (:error ctx))))

(defn- sieppari-compatible
  "Translate a Pedestal interceptor map into a Sieppari one by
  rewriting only the `:error` arity — `:enter` and `:leave`
  share a 1-arg shape across both libraries."
  [interceptor]
  (cond-> interceptor
          (:error interceptor)
          (update :error pedestal->sieppari-error)))

(def trace-span
  "Vector of interceptors that add OpenTelemetry server span support to HTTP requests.

  Uses clj-otel server-span-interceptors with :create-span? true, which:
  - Creates a new server span with parent extracted from incoming W3C headers
  - Sets the span as the current context so handlers can call (inject-traceparent)
  - Records HTTP response status and exceptions, ends span on leave or error

  Synchronous-only: :set-current-context? is true, which is appropriate because
  all Reitit/Sieppari interceptors and handlers run on the same thread.

  Each interceptor's `:error` stage is wrapped to translate the
  Pedestal `(ctx ex)` arity to Sieppari's `(ctx)` shape; see
  `pedestal->sieppari-error`."
  (mapv sieppari-compatible
        (trace-http/server-span-interceptors {:create-span? true})))
