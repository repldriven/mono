(ns ^:eftest/synchronized com.repldriven.mono.kafka.interface-test
  (:require
    [com.repldriven.mono.testcontainers.interface]

    [com.repldriven.mono.kafka.interface :as SUT]

    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.event.interface :as event]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as util]

    [clojure.core.async :as async]
    [clojure.test :refer [deftest is testing]])
  (:import
    (org.apache.kafka.clients.consumer ConsumerRecord)
    (org.apache.kafka.clients.producer.internals BuiltInPartitioner)))

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
         ;; The loop closes :c on its way out, so a take eventually returns
         ;; nil. Eventually, not immediately: if the polling thread reaches
         ;; its stop check before this put lands, it polls first, and then
         ;; races each record it fetched against the stop — so a record can
         ;; arrive before the close. Drain to the close, which is what
         ;; halting means here; asserting on the first take is asserting on
         ;; that race.
         (let [deadline (async/timeout 10000)
               closed? (loop []
                         (let [[v port] (async/alts!! [c deadline])]
                           (cond (= port deadline)
                                 false

                                 (nil? v)
                                 true

                                 :else
                                 (recur))))]
           (is closed? "channel closed after stop"))))
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
       (let [deadline (+ (util/now) 30000)]
         (while (and (< (count @received) (count pets)) (< (util/now) deadline))
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

;; topic-event and topic-command both have three partitions. That alone does
;; not make an unkeyed send land anywhere in particular: with no key the
;; built-in partitioner sticks to one partition until batch.size bytes have
;; gone to it, so a handful of small records all arrive together and "they
;; share a partition" passes whether or not a key was set. The assertions
;; below are therefore against the partition the key selects — the same
;; function the producer uses — rather than against records merely agreeing.
(def ^:private topic-partitions 3)

(def ^:private causation-ids ["party-a" "party-b"])

(defn- partition-for
  [key]
  (BuiltInPartitioner/partitionForKey (.getBytes ^String key "UTF-8")
                                      topic-partitions))

(defn- event-envelopes
  "Five events for each of two entities, interleaved, so an unkeyed send
  cannot be rescued by send order."
  []
  (let [correlation-id (str (util/uuidv7))]
    (vec (for [n (range 5)
               id causation-ids]
           (event/envelope (str "event-" n) id correlation-id)))))

(deftest event-partition-key-test
  (with-test-system
   [sys "classpath:kafka/application-test.yml"]
   (let [bus (system/instance sys [:kafka :bus])
         consumer (system/instance sys [:kafka :consumers :event-1])
         sent (event-envelopes)]
     (testing "events for one entity land on the partition its id hashes to"
       (is (apply not= (map partition-for causation-ids))
           "the two ids must hash apart, or this test proves nothing")
       (let [results (mapv #(event/publish bus % {:key (:causation-id %)})
                           sent)]
         (is (not-any? error/anomaly? results)
             (str "every publish succeeded: " (pr-str results))))
       (let [{:keys [c stop ack]} (SUT/receive consumer 500)
             received (take-with-timeout c (count sent) 30000)]
         (try
           (is (= (count sent) (count received)))
           (doseq [id causation-ids]
             (let [for-id (filterv #(= id (:causation-id (:data %))) received)
                   partitions (set (map #(.partition ^ConsumerRecord
                                                     (:message %))
                                        for-id))]
               (is (= #{(partition-for id)} partitions)
                   (str "every event for " id
                        " landed on the partition its key selects: "
                        partitions))
               (is (= (mapv :event (filter #(= id (:causation-id %)) sent))
                      (mapv #(:event (:data %)) for-id))
                   (str "and arrived for " id " in the order they were sent"))))
           (doseq [{:keys [message]} received]
             (SUT/acknowledge {:ack ack} message))
           (finally (async/put! stop :stop))))))))

(deftest event-anomaly-redelivery-test
  (with-test-system
   [sys "classpath:kafka/application-test.yml"]
   (let [bus (system/instance sys [:kafka :bus])
         attempts (atom 0)
         received (promise)
         envelope
         (event/envelope "party-registered" "party-1" (str (util/uuidv7)))]
     (testing "a handler that returns an anomaly gets the event again"
       ;; An anomaly is a failure the handler expected; committing it would
       ;; drop the event with nothing left to retry from. Exhausting the
       ;; redeliveries dead-letters it, the same way a throw does — see
       ;; dead-letter-test.
       (let [{:keys [stop]} (event/process bus
                                           (fn [data]
                                             (if (= 1 (swap! attempts inc))
                                               (error/fail :test/transient
                                                           {:message
                                                            "handler failed"})
                                               (deliver received data))))]
         (try (nom-test> [_ (event/publish bus envelope)])
              (let [data (deref received 30000 ::timeout)]
                (is (not= ::timeout data)
                    "the event was redelivered and processed on a later try")
                (when (not= ::timeout data)
                  (is (= (:id envelope) (:id data))
                      "and it is the same event, not a fresh one")))
              (is (> @attempts 1) "the first attempt did not commit")
              (finally (when stop (stop)))))))))

(deftest command-over-kafka-test
  (with-test-system
   [sys "classpath:kafka/command-test.yml"]
   (let [bus (system/instance sys [:kafka :bus])
         dispatcher (system/instance sys [:command :dispatcher])
         keyed-consumer (system/instance sys [:kafka :consumers :command-keyed])
         payload (.getBytes "pet-payload" "UTF-8")
         account-ids ["account-a" "account-b"]
         ;; command-id -> the account it is keyed on
         keyed (into {} (map (fn [id] [(str (util/uuidv7)) id])) account-ids)
         unkeyed-id (str (util/uuidv7))
         envelope (fn [id command]
                    {:id id
                     :command command
                     :correlation-id (str (util/uuidv7))
                     :causation-id nil
                     :traceparent nil
                     :tracestate nil
                     :payload payload
                     :reply-to nil})
         ;; One processor for the whole deftest: stopping a subscription
         ;; closes the Kafka consumer under it, so it cannot be started
         ;; again in this system.
         {:keys [stop]} (command/process
                         bus
                         (fn [envelope]
                           {:status "ACCEPTED" :payload (:payload envelope)})
                         {:command-channel :command
                          :command-response-channel :command-response})]
     (try
       (testing "a command sent over Kafka gets its reply back"
         ;; command knows nothing about the transport: it takes a bus, and
         ;; this one is Kafka. Nothing in the command brick changed to make
         ;; this work.
         (let [reply (command/send dispatcher
                                   (envelope unkeyed-id "create-pet")
                                   {:timeout-ms 30000})]
           (is (not (error/anomaly? reply))
               (str "command round-tripped over Kafka: " (pr-str reply)))
           (is (= "ACCEPTED" (:status reply)))
           (is (= "pet-payload" (String. ^bytes (:payload reply) "UTF-8"))
               "the payload survived Avro serialisation both ways")))
       (testing "a keyed command lands on the partition its key selects"
         ;; Commands for one account have to be processed in the order they
         ;; were sent, and the key is how that is asked for — supplied by
         ;; the caller, like the event path, rather than dug out of the
         ;; envelope.
         (is (apply not= (map partition-for account-ids))
             "the two account ids must hash apart, or this proves nothing")
         (doseq [[command-id account-id] keyed]
           (let [reply (command/send dispatcher
                                     (envelope command-id "update-account")
                                     {:timeout-ms 30000 :key account-id})]
             (is (not (error/anomaly? reply))
                 (str "keyed command round-tripped: " (pr-str reply)))))
         (let [{:keys [c stop ack]} (SUT/receive keyed-consumer 500)
               ;; the unkeyed command from the block above is on this topic
               ;; too, and is asserted on below
               received (take-with-timeout c (inc (count keyed)) 30000)
               records (into {}
                             (map (fn [{:keys [message data]}] [(:id data)
                                                                message]))
                             received)
               seen (into {}
                          (keep (fn [[id ^ConsumerRecord record]]
                                  (when (contains? keyed id)
                                    [id (.partition record)])))
                          records)]
           (try (is (= (set (keys keyed)) (set (keys seen)))
                    "both keyed commands were seen")
                (doseq [[command-id account-id] keyed]
                  (is (= (partition-for account-id) (get seen command-id))
                      (str "the command for "
                           account-id
                           " landed on the partition its key selects")))
                ;; A key is optional, and an absent one is absent: nothing
                ;; is derived to stand in for it, so the record carries a
                ;; null key and the partitioner places it as it sees fit.
                (is (nil? (some-> ^ConsumerRecord (get records unkeyed-id)
                                  (.key)))
                    "a command sent without a key has no key on the wire")
                (doseq [{:keys [message]} received]
                  (SUT/acknowledge {:ack ack} message))
                (finally (async/put! stop :stop)))))
       (finally (when stop (stop)))))))
