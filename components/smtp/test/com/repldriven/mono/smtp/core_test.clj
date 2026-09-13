(ns com.repldriven.mono.smtp.core-test
  (:require
    [com.repldriven.mono.smtp.core :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]])
  (:import
    (jakarta.mail Address SendFailedException)
    (jakarta.mail.internet InternetAddress)))

(def ^:private config
  {:host "smtp.example.test"
   :port 587
   :security :starttls
   :connection-timeout-ms 10000
   :timeout-ms 10000
   :properties {}})

(deftest session-properties-test
  (testing ":starttls enables and requires STARTTLS"
    (let [props (SUT/session-properties config)]
      (is (= "smtp.example.test" (get props "mail.smtp.host")))
      (is (= "587" (get props "mail.smtp.port")))
      (is (= "10000" (get props "mail.smtp.connectiontimeout")))
      (is (= "10000" (get props "mail.smtp.timeout")))
      (is (= "true" (get props "mail.smtp.starttls.enable")))
      (is (= "true" (get props "mail.smtp.starttls.required")))
      (is (= "true" (get props "mail.smtp.ssl.checkserveridentity")))
      (is (not (contains? props "mail.smtp.ssl.enable")))
      (is (not (contains? props "mail.smtp.auth")))))
  (testing ":tls enables SSL from the first byte"
    (let [props (SUT/session-properties (assoc config :security :tls))]
      (is (= "true" (get props "mail.smtp.ssl.enable")))
      (is (= "true" (get props "mail.smtp.ssl.checkserveridentity")))
      (is (not (contains? props "mail.smtp.starttls.enable")))))
  (testing ":none sets neither"
    (let [props (SUT/session-properties (assoc config :security :none))]
      (is (= "true" (get props "mail.smtp.ssl.checkserveridentity")))
      (is (not (contains? props "mail.smtp.ssl.enable")))
      (is (not (contains? props "mail.smtp.starttls.enable")))
      (is (not (contains? props "mail.smtp.starttls.required")))))
  (testing "a username sets mail.smtp.auth"
    (is (= "true"
           (get (SUT/session-properties (assoc config :username "user"))
                "mail.smtp.auth"))))
  (testing ":properties are merged last"
    (let [props (SUT/session-properties
                 (assoc config
                        :properties
                        {:mail.smtp.starttls.required false
                         "mail.smtp.auth.mechanisms" "XOAUTH2"}))]
      (is (= "false" (get props "mail.smtp.starttls.required")))
      (is (= "XOAUTH2" (get props "mail.smtp.auth.mechanisms"))))))

(defn- addresses
  [& xs]
  (into-array Address (map (fn [^String x] (InternetAddress. x)) xs)))

(deftest send-failure-test
  (testing "a SendFailedException reports each recipient's fate"
    (let [e (SendFailedException. "Invalid Addresses"
                                  nil
                                  (addresses "sent@example.test")
                                  (addresses "unsent@example.test")
                                  (addresses "bad@example.test"
                                             "worse@example.test"))
          result (SUT/send-failure (error/fail :smtp/send
                                               {:message "Failed to send"
                                                :exception e}))
          payload (error/payload result)]
      (is (= :smtp/send (error/kind result)))
      (is (= "Failed to send" (:message payload)))
      (is (= ["bad@example.test" "worse@example.test"]
             (:invalid-addresses payload)))
      (is (= ["unsent@example.test"] (:valid-unsent payload)))
      (is (= ["sent@example.test"] (:valid-sent payload)))))
  (testing "no addresses from Jakarta are empty vectors"
    (let [e (SendFailedException. "Failed" nil nil nil nil)
          payload (error/payload (SUT/send-failure
                                  (error/fail :smtp/send {:exception e})))]
      (is (= [] (:invalid-addresses payload)))
      (is (= [] (:valid-unsent payload)))
      (is (= [] (:valid-sent payload)))))
  (testing "any other exception leaves the anomaly unchanged"
    (let [anomaly (error/fail :smtp/send
                              {:message "Failed" :exception (Exception. "x")})]
      (is (= anomaly (SUT/send-failure anomaly))))))
