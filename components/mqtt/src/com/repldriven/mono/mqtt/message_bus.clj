(ns com.repldriven.mono.mqtt.message-bus
  (:require
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.mqtt.client :as client]
    [com.repldriven.mono.json.interface :as json]))

(defrecord MqttProducer [client topic qos]
  message-bus/Producer
    (send [_ message] (client/publish client topic (json/write-str message)))
    ;; MQTT has no partitions, so there is nothing for a key to select.
    (send [_ message _opts]
      (client/publish client topic (json/write-str message))))

(defrecord MqttConsumer [client topic qos]
  message-bus/Consumer
    (subscribe [_ handler-fn]
      (client/subscribe client
                        {topic qos}
                        (fn [_ _ ^bytes payload]
                          (handler-fn (json/read-str (String. payload
                                                              "UTF-8")))))
      {:topic topic})
    (unsubscribe [_] (client/unsubscribe client [topic]))
    ;; One client subscription per topic, so stopping it by name and
    ;; stopping the consumer's only one are the same act.
    (unsubscribe [_ _subscription] (client/unsubscribe client [topic])))
