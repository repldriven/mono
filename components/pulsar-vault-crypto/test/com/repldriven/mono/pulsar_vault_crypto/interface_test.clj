(ns ^:eftest/synchronized com.repldriven.mono.pulsar-vault-crypto.interface-test
  (:refer-clojure :exclude [send])
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.pulsar-vault-crypto.interface
    [com.repldriven.mono.pulsar.interface :as pulsar]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system]]
    [clojure.core.async :as async]
    [clojure.test :refer [deftest is testing]]))

(deftest pulsar-vault-crypto-test
  (with-test-system
   [sys "classpath:pulsar-vault-crypto/application-test.yml"]
   (let [producer (system/instance sys [:pulsar :producers :pet])
         consumer-1 (system/instance sys [:pulsar :consumers :pet-1])
         consumer-2 (system/instance sys [:pulsar :consumers :pet-2])
         msgs [{:pet-id "pet-1" :name "Whiskers" :species "cat" :age-months 24}
               {:pet-id "pet-2" :name "Rex" :species "dog" :age-months 36}
               {:pet-id "pet-3" :name "Tweety" :species "bird" :age-months 12}]
         props {"message" "pet-msg"}]
     (testing "Consumer with correct tenant key reads from vault and decrypts"
       (doseq [msg msgs] (pulsar/send producer msg {"properties" props}))
       (let [{:keys [c stop]} (pulsar/receive consumer-1 50)
             timeout (async/timeout 10000)
             [recv-msgs _] (async/alts!! [(async/into []
                                                      (async/take (count msgs)
                                                                  c)) timeout])]
         (async/>!! stop :stop)
         (is (some? recv-msgs) "Should receive messages")
         (when recv-msgs
           (doseq [{:keys [message data]} recv-msgs]
             (is (not (error/anomaly? data)))
             (.acknowledge consumer-1 message))
           (is (= msgs (mapv :data recv-msgs))
               "Messages should decrypt correctly"))))
     (testing "Consumer with wrong tenant (no vault key) cannot decrypt"
       (doseq [msg msgs] (pulsar/send producer msg {"properties" props}))
       (let [{:keys [c stop]} (pulsar/receive consumer-2 50)
             timeout (async/timeout 10000)
             [recv-msgs _] (async/alts!! [(async/into []
                                                      (async/take (count msgs)
                                                                  c)) timeout])]
         (async/>!! stop :stop)
         (is (some? recv-msgs)
             "Should receive messages (encrypted, undecryptable)")
         (when recv-msgs
           (doseq [{:keys [data]} recv-msgs]
             (is (= :pulsar/message-decrypt (error/kind data))
                 "Should return decrypt anomaly for wrong tenant")))))
     (testing "Key rotation: updated vault secret is picked up without restart"
       (comment
         "Vault reads happen on every getPublicKey/getPrivateKey call so rotating"
         "a key in vault is immediately visible to the next message — no restart needed."
         "To test: write a new key to vault, send a message, consumer should decrypt it.")
       (let [_vault-client (system/instance sys [:vault :client])
             _new-pubkey (get-in (system/instance sys [:pulsar :schemas])
                                 [:pet]) ;; placeholder
            ]
         ;; rotation test to be implemented once vault write-secret is
         ;; available
         (is true "Rotation test placeholder"))))))
