(ns ^:eftest/synchronized com.repldriven.mono.pulsar.interface-test
  (:refer-clojure :exclude [send])
  (:require
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.pulsar.interface :as SUT]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.event.interface :as event]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system]]
    [com.repldriven.mono.utility.interface :as util]
    [clojure.core.async :as async]
    [clojure.string :as string]
    [clojure.test :refer [deftest is testing]]))

(deftest pulsar-test
  (with-test-system
   [sys "classpath:pulsar/application-test.yml"]
   (let [admin (system/instance sys [:pulsar :admin])
         producer (system/instance sys [:pulsar :producers :pet])
         consumer-1 (system/instance sys [:pulsar :consumers :pet-1])
         consumer-2 (system/instance sys [:pulsar :consumers :pet-2])
         reader (system/instance sys [:pulsar :readers :pet])
         msgs [{:pet-id "pet-1" :name "Whiskers" :species "cat" :age-months 24}
               {:pet-id "pet-2" :name "Rex" :species "dog" :age-months 36}
               {:pet-id "pet-3" :name "Tweety" :species "bird" :age-months 12}]
         props {"message" "pet-msg"}]
     (testing
       "Pulsar namespace configuration enforces encryption and topic schema"
       (let [namespace-url
             (SUT/admin-namespace-url admin "tenant-1" "namespace-1")
             expected {"autoTopicCreation" {"topicType" "string"
                                            "allowAutoTopicCreation" false
                                            "defaultNumPartitions" 1}
                       "encryptionRequired" true
                       "isAllowAutoUpdateSchema" false
                       "schemaCompatibilityStrategy" "FULL"
                       "schemaValidationEnforced" true}]
         (doseq [[k v] expected]
           (let [url (string/join "/" [namespace-url k])
                 res (http/request {:url url :method :get})]
             (is (= v (http/res->body res)))))))
     (testing "Pulsar consumer with a matching decryption key can consume"
       (doseq [msg msgs] (SUT/send producer msg {"properties" props}))
       (let [{:keys [c stop]} (SUT/receive consumer-1 50)
             timeout (async/timeout 5000)
             [recv-msgs _] (async/alts!! [(async/into []
                                                      (async/take (count msgs)
                                                                  c)) timeout])]
         (async/>!! stop :stop)
         (is (some? recv-msgs) "Should receive messages")
         (when recv-msgs
           (doseq [{:keys [message data]} recv-msgs]
             (is (not (error/anomaly? data)))
             (.acknowledge consumer-1 message))
           (is (= msgs (mapv :data recv-msgs)) "Messages don't match"))))
     (testing "Pulsar consumer with a mismatching decryption key cannot consume"
       (doseq [msg msgs] (SUT/send producer msg {"properties" props}))
       (let [{:keys [c stop]} (SUT/receive consumer-2 50)
             timeout (async/timeout 5000)
             [recv-msgs _] (async/alts!! [(async/into []
                                                      (async/take (count msgs)
                                                                  c)) timeout])]
         (async/>!! stop :stop)
         (is (some? recv-msgs) "Should receive messages")
         (when recv-msgs
           (for [{:keys [data]} recv-msgs]
             (is (= :pulsar/message-decrypt (error/kind data))
                 "Should return decrypt anomaly for mismatched key")))))
     (testing "Pulsar reader with a matching decryption key can receive"
       (doseq [msg msgs] (SUT/send producer msg {"properties" props}))
       (let [{:keys [c stop]} (SUT/read reader 50)
             timeout (async/timeout 5000)
             [recv-msgs _] (async/alts!! [(async/into []
                                                      (async/take (count msgs)
                                                                  c)) timeout])]
         (async/>!! stop :stop)
         (is (some? recv-msgs) "Should receive messages")
         (is (= msgs (mapv :data recv-msgs)) "Messages don't match"))))))

(def ^:private causation-ids ["party-a" "party-b"])

(defn- take-with-timeout
  "Take `n` messages from `c`, giving up after `ms`. Returns what arrived."
  [c n ms]
  (let [deadline (async/timeout ms)]
    (loop [acc []]
      (if (= n (count acc))
        acc
        (let [[v port] (async/alts!! [c deadline])]
          (if (or (= port deadline) (nil? v)) acc (recur (conj acc v))))))))

(defn- event-envelopes
  "Five events for each of two entities, interleaved, so an unkeyed send
  cannot be rescued by send order."
  []
  (let [correlation-id (str (util/uuidv7))]
    (vec (for [n (range 5)
               id causation-ids]
           (event/envelope (str "event-" n) id correlation-id)))))

;; The same two properties the kafka brick is tested for, over Pulsar:
;; message-bus is the seam both sit behind, so a workspace that swaps one
;; for the other has to get keying and redelivery from either.
(deftest event-over-pulsar-test
  (with-test-system
   [sys "classpath:pulsar/application-test.yml"]
   (let [bus (system/instance sys [:pulsar :bus])
         consumer (system/instance sys [:pulsar :consumers :event])
         sent (event-envelopes)]
     (testing "events for one entity land on one partition, in order"
       ;; topic-event has three partitions and Pulsar's default router
       ;; round-robins when a message has no key, so without one these
       ;; five would be spread across all three.
       (let [results (mapv #(event/publish bus % {:key (:causation-id %)})
                           sent)]
         (is (not-any? error/anomaly? results)
             (str "every publish succeeded: " (pr-str results))))
       (let [{:keys [c stop]} (SUT/receive consumer 50)
             received (take-with-timeout c (count sent) 30000)]
         (try
           (is (= (count sent) (count received)))
           (let [topics
                 (into
                  {}
                  (map (fn [id]
                         (let [for-id (filterv #(= id (:causation-id (:data %)))
                                               received)]
                           [id
                            {:partitions (set (map #(.getTopicName (:message %))
                                                   for-id))
                             :order (mapv #(:event (:data %)) for-id)}])))
                  causation-ids)]
             (doseq [id causation-ids]
               (let [{:keys [partitions order]} (get topics id)]
                 (is (= 1 (count partitions))
                     (str "every event for " id
                          " landed on one partition: " partitions))
                 (is
                  (= (mapv :event (filter #(= id (:causation-id %)) sent))
                     order)
                  (str "and arrived for " id " in the order they were sent"))))
             (is (apply distinct?
                        (map #(:partitions (get topics %)) causation-ids))
                 "and the two entities were routed apart, not merely batched"))
           (doseq [{:keys [message]} received] (.acknowledge consumer message))
           (finally (async/>!! stop :stop))))))))

(deftest event-anomaly-redelivery-over-pulsar-test
  (with-test-system
   [sys "classpath:pulsar/application-test.yml"]
   (let [bus (system/instance sys [:pulsar :bus])
         attempts (atom 0)
         received (promise)
         envelope
         (event/envelope "party-registered" "party-1" (str (util/uuidv7)))]
     (testing "a handler that returns an anomaly gets the event again"
       ;; The Pulsar consumer negative-acknowledges on a throw, so the
       ;; anomaly `event/process` now rethrows becomes a redelivery here
       ;; exactly as it does over Kafka.
       (let [{:keys [stop]} (event/process bus
                                           (fn [data]
                                             (if (= 1 (swap! attempts inc))
                                               (error/fail :test/transient
                                                           {:message
                                                            "handler failed"})
                                               (deliver received data)))
                                           {:event-channel :event-retry})]
         (try (is (not (error/anomaly? (event/publish bus
                                                      envelope
                                                      {:event-channel
                                                       :event-retry}))))
              (let [data (deref received 30000 ::timeout)]
                (is (not= ::timeout data)
                    "the event was redelivered and processed on a later try")
                (when (not= ::timeout data)
                  (is (= (:id envelope) (:id data))
                      "and it is the same event, not a fresh one")))
              (is (> @attempts 1) "the first attempt did not acknowledge")
              (finally (when stop (stop)))))))))
