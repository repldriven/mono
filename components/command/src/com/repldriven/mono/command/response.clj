(ns com.repldriven.mono.command.response
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- ->command-envelope
  [{:keys [command-id causation-id correlation-id status payload reason
           message]}]
  {:id (str (utility/uuidv7))
   :correlation-id correlation-id
   :command-id command-id
   :causation-id (or causation-id "")
   :traceparent (or (telemetry/inject-traceparent) "")
   :tracestate nil
   :status status
   :payload payload
   :reason reason
   :message message})

(defn- ->command-rejection
  [{:keys [command-id causation-id correlation-id anomaly]}]
  (->command-envelope {:command-id command-id
                       :causation-id causation-id
                       :correlation-id correlation-id
                       :status "REJECTED"
                       :reason (str (error/kind anomaly))
                       :message (:message (error/payload anomaly))}))

(defn- ->command-error
  [{:keys [command-id causation-id correlation-id anomaly]}]
  (->command-envelope {:command-id command-id
                       :causation-id causation-id
                       :correlation-id correlation-id
                       :status "FAILED"
                       :reason (str (error/kind anomaly))
                       :message (:message (error/payload anomaly))}))

(defn command-response
  "Build a structured command response from a command
  envelope and its process-fn result.

  On rejection anomaly: REJECTED with reason and message.
  On error anomaly: FAILED with reason and message.
  On {:status \"ACCEPTED\" :payload ...}: ACCEPTED with
    payload."
  [envelope result]
  (let [{:keys [id command-id correlation-id]} envelope]
    (cond (error/rejection? result)
          (->command-rejection {:command-id command-id
                                :causation-id id
                                :correlation-id
                                correlation-id
                                :anomaly result})
          (error/error? result)
          (->command-error {:command-id command-id
                            :causation-id id
                            :correlation-id
                            correlation-id
                            :anomaly result})
          :else
          (->command-envelope {:command-id command-id
                               :causation-id id
                               :correlation-id correlation-id
                               :status "ACCEPTED"
                               :payload (:payload result)}))))

