(ns com.repldriven.mono.smtp.system-test
  (:require
    [com.repldriven.mono.smtp.system :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [malli.core :as m]

    [clojure.test :refer [deftest is testing]]))

(def ^:private config
  (assoc (:system/config SUT/client) :host "smtp.example.test"))

(defn- valid?
  [config]
  (m/validate (:system/config-schema SUT/client) config))

(deftest client-config-schema-test
  (testing "the defaults with a host validate"
    (is (valid? config))
    (is (valid? (assoc config
                       :username "user"
                       :password "secret"
                       :from {:address "noreply@example.test" :name "Example"}
                       :properties {:mail.smtp.auth.mechanisms "XOAUTH2"}))))
  (testing "a config without a host is refused"
    (is (not (valid? (dissoc config :host)))))
  (testing "an unknown security is refused"
    (is (not (valid? (assoc config :security :fast))))))

(deftest client-start-test
  (testing "starting the client with no reachable server succeeds"
    (let [start (:system/start SUT/client)
          instance (start {:system/config
                           (assoc config :host "unreachable.invalid")})]
      (is (not (error/anomaly? instance)))
      (is (m/validate (:system/instance-schema SUT/client) instance)))))
