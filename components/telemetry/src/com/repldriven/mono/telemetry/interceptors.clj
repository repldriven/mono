(ns com.repldriven.mono.telemetry.interceptors
  "Interceptors for distributed tracing and request validation."
  (:require
    [com.repldriven.mono.log.interface :as log]
    [steffan-westcott.clj-otel.api.trace.http :as trace-http]))

(def require-idempotency-key
  "Interceptor that validates Idempotency-Key header is present.

  Returns 400 Bad Request if the header is missing."
  {:name ::require-idempotency-key
   :enter (fn [ctx]
            (log/debugf
             "telemetry.interceptors/require-idempotency-key(enter): [headers=]"
             (get-in ctx [:request :headers]))
            (if (some? (get-in ctx [:request :headers "idempotency-key"]))
              ctx
              (assoc ctx
                     :response
                     {:status 400
                      :body {:error "Missing Idempotency-Key header"}})))})

(def trace-span
  "Vector of interceptors that add OpenTelemetry server span support to HTTP requests.

  Uses clj-otel server-span-interceptors with :create-span? true, which:
  - Creates a new server span with parent extracted from incoming W3C headers
  - Sets the span as the current context so handlers can call (inject-traceparent)
  - Records HTTP response status and exceptions, ends span on leave or error

  Synchronous-only: :set-current-context? is true, which is appropriate because
  all Reitit/Sieppari interceptors and handlers run on the same thread."
  (trace-http/server-span-interceptors {:create-span? true}))
