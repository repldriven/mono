(ns com.repldriven.mono.command.dispatcher
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.utility.interface :as utility]))

(defn start
  "Start a shared command-response dispatcher.

  Subscribes once to command-response-channel on bus. Each
  arriving response is matched to a waiting promise by
  command-id — the per-send identity, so a straggling reply from
  an earlier attempt cannot satisfy a later one's waiter. Handles
  both keyword (local) and string (Pulsar) keys.

  Args:
  - bus: message-bus instance
  - command-channel: keyword for sending commands
  - command-response-channel: keyword for receiving responses

  Returns dispatcher map."
  [bus command-channel command-response-channel]
  (let [pending (atom {})
        sub (message-bus/subscribe
             bus
             command-response-channel
             (fn [response]
               (let [command-id (or (:command-id response)
                                    (get response "command-id"))]
                 (when-let [p (get @pending command-id)]
                   (deliver p response)))))
        stop-fn (fn [] (message-bus/unsubscribe bus command-response-channel))]
    (if (error/anomaly? sub)
      ;; nosemgrep: no-raw-throw
      (throw (ex-info "Failed to start command dispatcher" {:anomaly sub}))
      {:bus bus
       :command-channel command-channel
       :pending pending
       :stop-fn stop-fn})))

(defn stop
  "Stop the dispatcher and unsubscribe from its response channel."
  [{:keys [stop-fn]}]
  (when stop-fn (stop-fn)))

(defn send
  "Send a command and await its reply via the shared dispatcher.

  Mints a fresh command-id for this send, stamps it on the
  command envelope, registers a promise in :pending keyed by that
  command-id, sends the command, derefs with timeout, then removes
  it from :pending. The command-id is unique per send, so a retry
  (which reuses the idempotency key and correlation-id) still gets
  its own waiter.

  Args:
  - dispatcher: started dispatcher map
  - command: command envelope map
  - opts: optional map with keys:
    - :timeout-ms - timeout in milliseconds (default 10000)

  Returns response map or anomaly."
  ([dispatcher command] (send dispatcher command {}))
  ([{:keys [bus command-channel pending]} command opts]
   (let [{:keys [timeout-ms] :or {timeout-ms 10000}} opts
         command-id (str (utility/uuidv7))
         command (assoc command :command-id command-id)
         p (promise)]
     (swap! pending assoc command-id p)
     (let [pub (message-bus/send bus command-channel command)]
       (if (error/anomaly? pub)
         (do (swap! pending dissoc command-id) pub)
         (let [result (deref p timeout-ms ::timeout)]
           (swap! pending dissoc command-id)
           (if (= result ::timeout)
             (error/fail :command/timeout {:message "Command reply timed out"})
             result)))))))
