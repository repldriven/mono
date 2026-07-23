(ns ^:eftest/synchronized com.repldriven.mono.kafka.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface ;; extends
                                                 ;; `system/components`

    [com.repldriven.mono.kafka.interface :as SUT]

    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.core.async :as async]
    [clojure.test :refer [deftest is testing]]))

(def ^:private pets
  [{:pet-id "pet-1" :name "Whiskers" :species "cat" :age-months 24}
   {:pet-id "pet-2" :name "Rex" :species "dog" :age-months 36}
   {:pet-id "pet-3" :name "Tweety" :species "bird" :age-months 12}])

(defn- take-with-timeout
  "Take `n` messages from `c`, giving up after `ms`. Returns what arrived."
  [c n ms]
  (let [deadline (async/timeout ms)]
    (loop [acc []]
      (if (= n (count acc))
        acc
        (let [[v port] (async/alts!! [c deadline])]
          (if (or (= port deadline) (nil? v)) acc (recur (conj acc v))))))))

(deftest kafka-test
  (with-test-system
   [sys "classpath:kafka/application-test.yml"]
   (let [producer (system/instance sys [:kafka :producers :pet])
         consumer (system/instance sys [:kafka :consumers :pet-1])]
     (testing "a batch poll delivers every record, one per take"
       (nom-test> [_ (doseq [pet pets] (SUT/send producer pet))])
       (let [{:keys [c stop ack]} (SUT/receive consumer 500)
             received (take-with-timeout c (count pets) 20000)]
         (try (is (= (count pets) (count received)))
              (is (= (set pets) (set (map :data received)))
                  "values round-trip through avro serialisation")
              (doseq [{:keys [message]} received]
                (SUT/acknowledge {:ack ack} message))
              (finally (async/put! stop :stop)))))
     (testing "stop halts the loop even when nobody is reading"
       (let [{:keys [c stop]} (SUT/receive (system/instance sys
                                                            [:kafka :consumers
                                                             :pet-2])
                                           500)]
         (async/put! stop :stop)
         ;; the loop closes :c on its way out, so a take returns nil
         ;; promptly
         (let [[v _] (async/alts!! [c (async/timeout 10000)])]
           (is (nil? v) "channel closed after stop"))))
     (testing "a failing send is an anomaly, not a throw"
       ;; an unserialisable value: the schema expects a map of pet fields
       (let [result (SUT/send producer "not-a-pet")]
         (is (error/anomaly? result))
         (is (= :avro/serialize (error/kind result))))))))

(deftest message-bus-test
  (with-test-system
   [sys "classpath:kafka/application-test.yml"]
   (let [bus (system/instance sys [:kafka :bus])
         received (atom [])
         deliveries (atom 0)
         failures (atom 0)]
     (testing "a handler that throws does not kill the subscription"
       (message-bus/subscribe bus
                              :pet
                              (fn [data]
                                (swap! deliveries inc)
                                ;; fail the first message once, then accept
                                ;; everything: the subscription must
                                ;; survive and the message must come back
                                (if (and (= "pet-1" (:pet-id data))
                                         (zero? @failures))
                                  (do (swap! failures inc)
                                      ;; nosemgrep: no-raw-throw
                                      (throw (ex-info "handler failed" {})))
                                  (swap! received conj data))))
       (nom-test> [_ (doseq [pet pets] (message-bus/send bus :pet pet))])
       (let [deadline (+ (System/currentTimeMillis) 30000)]
         (while (and (< (count @received) (count pets))
                     (< (System/currentTimeMillis) deadline))
           (Thread/sleep 200)))
       (is (= 1 @failures) "the handler threw once")
       (is (> @deliveries (count pets))
           (str "seeking back rewinds the whole partition, so messages after "
                "the failed one are redelivered too: "
                @deliveries
                " deliveries for "
                (count pets)
                " messages"))
       (is (= (set pets) (set @received))
           "every message arrived, including the redelivered one")
       (message-bus/unsubscribe bus :pet)))))

(deftest dead-letter-test
  (with-test-system
   [sys "classpath:kafka/application-test.yml"]
   (let [bus (system/instance sys [:kafka :bus])
         dlq (system/instance sys [:kafka :consumers :pet-dlq])
         poison (first pets)
         attempts (atom 0)]
     (testing "a message that exhausts its redeliveries is dead-lettered"
       ;; pet-2 is configured with max-redeliveries 2 and a DLQ producer
       (message-bus/subscribe bus
                              :pet
                              (fn [data]
                                (when (= (:pet-id poison) (:pet-id data))
                                  (swap! attempts inc)
                                  ;; nosemgrep: no-raw-throw
                                  (throw (ex-info "always fails" {})))))
       (nom-test> [_ (message-bus/send bus :pet poison)])
       (let [{:keys [c stop]} (SUT/receive dlq 500)
             [v _] (async/alts!! [c (async/timeout 30000)])]
         (try (is (some? v) "the poison message reached the dead-letter topic")
              ;; forwarded as it arrived: bytes, not a parsed map
              (is (bytes? (:data v)) "dead-lettered values are raw bytes")
              (is (= 2 @attempts) "it was retried up to max-redeliveries")
              (finally (async/put! stop :stop)
                       (message-bus/unsubscribe bus :pet))))))))

(deftest command-over-kafka-test
  (with-test-system
   [sys "classpath:kafka/command-test.yml"]
   (let [bus (system/instance sys [:kafka :bus])
         dispatcher (system/instance sys [:command :dispatcher])
         payload (.getBytes "pet-payload" "UTF-8")]
     (testing "a command sent over Kafka gets its reply back"
       ;; command knows nothing about the transport: it takes a bus, and
       ;; this one is Kafka. Nothing in the command brick changed to make
       ;; this work.
       (let [{:keys [stop]}
             (command/process
              bus
              (fn [envelope] {:status "ACCEPTED" :payload (:payload envelope)})
              {:command-channel :command
               :command-response-channel :command-response})]
         (try (let [reply (command/send dispatcher
                                        {:id (str (random-uuid))
                                         :command "create-pet"
                                         :correlation-id (str (random-uuid))
                                         :causation-id nil
                                         :traceparent nil
                                         :tracestate nil
                                         :payload payload
                                         :reply-to nil}
                                        {:timeout-ms 30000})]
                (is (not (error/anomaly? reply))
                    (str "command round-tripped over Kafka: " (pr-str reply)))
                (is (= "ACCEPTED" (:status reply)))
                (is (= "pet-payload" (String. ^bytes (:payload reply) "UTF-8"))
                    "the payload survived Avro serialisation both ways"))
              (finally (when stop (stop)))))))))
