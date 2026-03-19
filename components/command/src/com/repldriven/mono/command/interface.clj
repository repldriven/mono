(ns com.repldriven.mono.command.interface
  (:refer-clojure :exclude [send])
  (:require
    com.repldriven.mono.command.system
    [com.repldriven.mono.command.dispatcher :as dispatcher]
    [com.repldriven.mono.command.processor :as processor]
    [com.repldriven.mono.command.request :as request]
    [com.repldriven.mono.command.response :as response]))

(defn req->command-request
  "Build a command envelope from an HTTP request.

  Args:
  - req: HTTP request map (reads idempotency-key and
    correlation-id from headers)
  - command: command name string

  Returns a command envelope map. Assoc :payload before
  sending."
  [req command]
  (request/req->command-request req command))

(defn command-response
  "Build a structured command response from a command
  envelope and its process-fn result.

  On success: status ACCEPTED with payload.
  On anomaly: status FAILED with error details."
  [command result]
  (response/command-response command result))

(defn process
  "Process command envelopes via message-bus.

  Args:
  - bus: message-bus instance
  - process-fn: function that takes a command envelope
    and returns a result map or anomaly
  - opts: optional map (reserved for future use)

  Returns: {:stop (fn [])} — call stop to unsubscribe"
  ([bus process-fn] (processor/process bus process-fn))
  ([bus process-fn opts] (processor/process bus process-fn opts)))

(defn send
  "Send a command via dispatcher and wait for reply.

  Args:
  - dispatcher: started dispatcher map
  - command: command envelope map
  - opts: optional map with keys:
    - :timeout-ms - timeout in milliseconds (default 10000)

  Returns: response map or anomaly"
  ([d command] (dispatcher/send d command))
  ([d command opts] (dispatcher/send d command opts)))
