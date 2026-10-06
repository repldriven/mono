(ns com.repldriven.mono.command.interface-test
  (:require
    [com.repldriven.mono.command.interface :as SUT]

    [com.repldriven.mono.command.dispatcher :as dispatcher]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer [with-test-system]]
    [com.repldriven.mono.test-telemetry.interface :as test-telemetry]
    [com.repldriven.mono.utility.interface :as util]

    [clojure.test :refer [deftest is testing]])
  (:import
    (io.opentelemetry.api.common AttributeKey)
    (io.opentelemetry.api.trace StatusCode)))

(deftest processor-uncaught-throw-yields-failed-reply-test
  (with-test-system
   [sys "classpath:command/application-local-test.yml"]
   (let [bus (system/instance sys [:message-bus :bus])]
     (testing
       "an uncaught throw in process-fn becomes a FAILED reply and
               does not wedge the channel"
       (let [calls (atom 0)
             process-fn (fn [_envelope]
                          (if (= 1 (swap! calls inc))
                            ;; nosemgrep: no-raw-throw
                            (throw (ex-info "boom" {}))
                            {:status "ACCEPTED" :payload nil}))
             replies (atom [])
             done (promise)]
         (SUT/process bus
                      process-fn
                      {:command-channel :command
                       :command-response-channel :command-response})
         (message-bus/subscribe bus
                                :command-response
                                (fn [resp]
                                  (swap! replies conj resp)
                                  (when (= 2 (count @replies))
                                    (deliver done true))))
         (message-bus/send bus
                           :command
                           {:command "c" :id "1" :correlation-id "corr-1"})
         (message-bus/send bus
                           :command
                           {:command "c" :id "2" :correlation-id "corr-2"})
         (is (not= ::timeout (deref done 5000 ::timeout))
             "both commands must produce a reply")
         (let [by-corr (into {} (map (juxt :correlation-id identity)) @replies)]
           (is (= "FAILED" (:status (get by-corr "corr-1")))
               "the throwing command yields a FAILED reply, not a hang")
           (is (= "ACCEPTED" (:status (get by-corr "corr-2")))
               "the channel keeps consuming after a throw")))))))

(deftest per-attempt-command-id-matching-test
  (with-test-system
   [sys "classpath:command/application-local-test.yml"]
   (let [bus (system/instance sys [:message-bus :bus])
         d (dispatcher/start bus :command :command-response {:timeout-ms 5000})
         ;; :payload marker -> the command-id the dispatcher minted for it
         seen (atom {})]
     (testing
       "two concurrent sends sharing a correlation-id each mint a
              distinct command-id and receive their own reply"
       (message-bus/subscribe
        bus
        :command
        (fn [cmd] (swap! seen assoc (:payload cmd) (:command-id cmd))))
       (let [f1 (future (SUT/send d
                                  {:command "c"
                                   :id "shared-key"
                                   :correlation-id "shared"
                                   :payload "cmd-1"}))
             f2 (future (SUT/send d
                                  {:command "c"
                                   :id "shared-key"
                                   :correlation-id "shared"
                                   :payload "cmd-2"}))]
         (loop [tries 0]
           (when (and (< (count @seen) 2) (< tries 100))
             (Thread/sleep 20)
             (recur (inc tries))))
         (is (= 2 (count @seen)) "both commands were published")
         (let [id-1 (get @seen "cmd-1")
               id-2 (get @seen "cmd-2")]
           (is
            (and id-1 id-2 (not= id-1 id-2))
            "each send mints a distinct command-id despite the shared
                      correlation-id")
           ;; reply out of order — cmd-2's reply first — to prove a reply
           ;; only ever satisfies the waiter for its own command-id
           (message-bus/send bus
                             :command-response
                             {:command-id id-2
                              :correlation-id "shared"
                              :status "ACCEPTED"
                              :payload "reply-2"})
           (message-bus/send bus
                             :command-response
                             {:command-id id-1
                              :correlation-id "shared"
                              :status "ACCEPTED"
                              :payload "reply-1"})
           (let [r1 (deref f1 5000 ::timeout)
                 r2 (deref f2 5000 ::timeout)]
             (is (= "reply-1" (:payload r1))
                 "the cmd-1 send resolves to the reply for its command-id")
             (is (= "reply-2" (:payload r2))
                 "the cmd-2 send resolves to the reply for its command-id")))))
     (dispatcher/stop d))))

(defn- waited-ms
  "How long `send` waited before it gave up on a reply that never came,
  in milliseconds, and the anomaly it gave up with."
  [send]
  (let [started (util/nanos)
        result (send)]
    [(quot (- (util/nanos) started) 1000000) result]))

(deftest timeout-default-test
  (with-test-system
   [sys "classpath:command/application-local-test.yml"]
   (testing "a dispatcher whose configuration sets no timeout waits 10 s"
     (is (= 10000
            (:timeout-ms (system/instance sys [:command :dispatcher])))))))

(deftest timeout-precedence-test
  (with-test-system
   [sys "classpath:command/application-local-test.yml"]
   (let [bus (system/instance sys [:message-bus :bus])
         d (dispatcher/start bus
                             :command
                             :command-response
                             {:timeout-ms 100 :command-timeouts-ms {:slow 400}})
         unanswered (fn [command opts]
                      (waited-ms (fn []
                                   (SUT/send d
                                             {:command command
                                              :id command
                                              :correlation-id command}
                                             opts))))]
     (testing "a command the configuration names waits its own timeout"
       (let [[ms result] (unanswered "slow" {})]
         (is (= :command/timeout (error/kind result)))
         (is (<= 400 ms 1400) (str ms "ms"))))
     (testing "any other command waits the dispatcher's timeout"
       (let [[ms result] (unanswered "other" {})]
         (is (= :command/timeout (error/kind result)))
         (is (<= 100 ms 399) (str ms "ms"))))
     (testing "a send's own timeout comes first"
       (let [[ms result] (unanswered "slow" {:timeout-ms 50})]
         (is (= :command/timeout (error/kind result)))
         (is (<= 50 ms 399) (str ms "ms"))))
     (dispatcher/stop d))))

(defn- awaited-span
  "The closed span named `span-name` whose `command` attribute is
  `command`, once it closes: `process-command` sends its reply from
  inside the span, so the caller can be unblocked before it ends."
  [otel span-name command]
  (loop [n 0]
    (let [span (->> (test-telemetry/finished-spans otel)
                    (filter (fn [s]
                              (and (= span-name (.getName s))
                                   (= command
                                      (.get (.getAttributes s)
                                            (AttributeKey/stringKey
                                             "command"))))))
                    first)]
      (if (or span (>= n 100))
        span
        (do (Thread/sleep 20) (recur (inc n)))))))

(defn- outcome
  [span]
  {:status (.get (.getAttributes span)
                 (AttributeKey/stringKey "command.status"))
   :reason (.get (.getAttributes span)
                 (AttributeKey/stringKey "command.reason"))
   :error? (= StatusCode/ERROR (.getStatusCode (.getStatus span)))})

(deftest spans-record-the-outcome-test
  (with-test-system
   [sys "classpath:command/application-traced-test.yml"]
   (let [bus (system/instance sys [:message-bus :bus])
         otel (system/instance sys [:telemetry :otel-sdk])
         d (dispatcher/start bus :command :command-response {:timeout-ms 5000})]
     (SUT/process
      bus
      (fn [{:keys [command]}]
        (case command
          "outcome-rejected" (error/reject :outcome/declined
                                           {:message "declined"})
          "outcome-failed" (error/fail :outcome/broken {:message "broken"})
          {:status "ACCEPTED" :payload nil}))
      {:command-channel :command :command-response-channel :command-response})
     (doseq [[command expected]
             [["outcome-accepted"
               {:status "ACCEPTED" :reason nil :error? false}]
              ["outcome-rejected"
               {:status "REJECTED" :reason ":outcome/declined" :error? false}]
              ["outcome-failed"
               {:status "FAILED" :reason ":outcome/broken" :error? true}]]]
       (testing (str command " is recorded on both spans")
         (SUT/send d {:command command :id command :correlation-id command})
         (doseq [span-name ["process-command" "command-send"]]
           (let [span (awaited-span otel span-name command)]
             (is (some? span) (str span-name " closed"))
             (is (= expected (outcome span)) span-name))))))))
