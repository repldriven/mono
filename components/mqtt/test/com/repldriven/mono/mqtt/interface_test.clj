(ns com.repldriven.mono.mqtt.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.mqtt.interface :as SUT]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system]]
    [clojure.test :refer [deftest is testing]]))

(deftest mqtt-publish-subscribe-test
  (testing "MQTT client should publish and subscribe to messages"
    (with-test-system [sys "classpath:mqtt/application-test.yml"]
                      (let [client (system/instance sys [:mqtt :client])
                            topic "Hello"
                            message "World"
                            p (promise)]
                        (SUT/subscribe client
                                       {topic 0}
                                       (fn [_ _ ^bytes payload]
                                         (deliver p (String. payload "UTF-8"))))
                        (SUT/publish client topic message)
                        (is (= @p message))))))
