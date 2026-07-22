(ns com.repldriven.mono.command.processor
  (:require
    [com.repldriven.mono.command.response :as response]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.telemetry.interface :as telemetry]))

(defn- log-line
  "One-line dispatch trace, mirroring the server's request-log
  interceptor (`POST /v1/parties 201 12.3ms`). Logged on every
  command completion so a missing line means the consumer never
  received the envelope.

  Anomalous results get logged WITH the original anomaly (not the
  flattened response envelope) so the kind, message, and any
  underlying exception class/message survive into the log. The
  response builder only carries `:message` forward, which strips
  the FDB/library cause text — exactly what we need to diagnose
  things like unique-index violations or schema mismatches."
  [channel command result resp start-ns]
  (let [ms (/ (- (System/nanoTime) start-ns) 1e6)
        {:keys [status]} resp]
    (log/info
     (if (error/anomaly? result)
       (format "processor: %s %s %s %.1fms %s"
               (name channel)
               (or command "?")
               status
               ms
               (error/format-anomaly result))
       (format "processor: %s %s %s %.1fms"
               (name channel)
               (or command "?")
               status
               ms)))))

(defn process
  "Process command envelopes via message-bus.

  Extracts the parent trace context from each incoming
  envelope and creates a span that covers both the
  process-fn call and the response send. This ensures
  inject-traceparent in the response has an active span.

  The process-fn receives the raw command envelope
  (including payload bytes) and is responsible for
  deserializing the payload.

  Args:
  - bus: message-bus instance
  - process-fn: function that takes a command envelope
    and returns a result map or anomaly
  - opts: optional map with keys:
    - :command-channel - keyword for receiving commands
    - :command-response-channel - keyword for sending
      responses

  Returns: {:stop (fn [])} — call stop to unsubscribe"
  ([bus process-fn] (process bus process-fn {}))
  ([bus process-fn opts]
   (let [{:keys [command-channel command-response-channel]
          :or {command-channel :command
               command-response-channel :command-response}}
         opts]
     (message-bus/subscribe
      bus
      command-channel
      (fn [data]
        (let [parent-ctx (telemetry/extract-parent-context data)
              start-ns (System/nanoTime)]
          (telemetry/with-span-parent
           "process-command"
           parent-ctx
           (select-keys data [:id :command :correlation-id :causation-id])
           (fn []
             ;; process-fn is expected to return anomalies, not throw —
             ;; but an unexpected throw must not escape the consumer loop.
             ;; Convert it to a FAILED result so a reply is always sent
             ;; and the caller never hangs waiting.
             (let [result (error/try-nom
                           :command/uncaught
                           "Command processing threw an uncaught exception"
                           (process-fn data))
                   resp (response/command-response data result)]
               ;; Log the ORIGINAL result so any captured exception
               ;; surfaces to the operator log; the response sent
               ;; back to the API is built from the same result but
               ;; carries only the kind+message — no exception
               ;; details leak to clients.
               (log-line command-channel
                         (:command data)
                         result
                         resp
                         start-ns)
               (message-bus/send bus command-response-channel resp)))))))
     {:stop (fn [] (message-bus/unsubscribe bus command-channel))})))
