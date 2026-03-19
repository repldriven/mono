(ns com.repldriven.mono.command.request
  (:require
    [com.repldriven.mono.telemetry.interface :as telemetry]))

(defn- req->ids
  [req]
  (let [idempotency-key (get-in req [:headers "idempotency-key"])
        correlation-id (or (get-in req [:headers "correlation-id"])
                           idempotency-key)]
    [idempotency-key correlation-id]))

(defn req->command-request
  "Build a command envelope from an HTTP request.

  Args:
  - req: HTTP request map (reads idempotency-key and
    correlation-id from headers)
  - command: command name string

  Returns a command envelope map. Assoc :payload before
  sending."
  [req command]
  (let [[idempotency-key correlation-id] (req->ids req)]
    {:command command
     :id idempotency-key
     :correlation-id correlation-id
     :causation-id nil
     :traceparent (telemetry/inject-traceparent)
     :tracestate nil
     :payload nil
     :reply-to nil}))
