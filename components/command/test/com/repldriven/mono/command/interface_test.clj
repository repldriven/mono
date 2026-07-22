(ns com.repldriven.mono.command.interface-test
  (:require
    [com.repldriven.mono.command.dispatcher :as dispatcher]
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer [with-test-system]]

    [clojure.test :refer [deftest is testing]]))

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
         (command/process bus
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
         d (dispatcher/start bus :command :command-response)
         ;; :payload marker -> the command-id the dispatcher minted for it
         seen (atom {})]
     (testing
       "two concurrent sends sharing a correlation-id each mint a
              distinct command-id and receive their own reply"
       (message-bus/subscribe
        bus
        :command
        (fn [cmd] (swap! seen assoc (:payload cmd) (:command-id cmd))))
       (let [f1 (future (command/send d
                                      {:command "c"
                                       :id "shared-key"
                                       :correlation-id "shared"
                                       :payload "cmd-1"}))
             f2 (future (command/send d
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
