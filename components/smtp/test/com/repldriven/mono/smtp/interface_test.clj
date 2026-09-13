(ns com.repldriven.mono.smtp.interface-test
  (:require
    [com.repldriven.mono.smtp.interface :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]))

(def ^:private message
  {:from "sender@example.test"
   :to ["a@example.test" "b@example.test"]
   :cc ["c@example.test"]
   :bcc ["d@example.test"]
   :reply-to "replies@example.test"
   :subject "Welcome"
   :text "Hello"})

(defn- header
  [text header-name]
  (second (re-find (re-pattern (str "(?m)^" header-name ": (.*)\r?$")) text)))

(deftest render-headers-test
  (let [text (SUT/render message)]
    (testing "From, To, Cc, Reply-To and Subject are as given"
      (is (= "sender@example.test" (header text "From")))
      (is (= "a@example.test, b@example.test" (header text "To")))
      (is (= "c@example.test" (header text "Cc")))
      (is (= "replies@example.test" (header text "Reply-To")))
      (is (= "Welcome" (header text "Subject"))))
    (testing "the Bcc header is omitted"
      (is (nil? (header text "Bcc")))
      (is (not (str/includes? text "d@example.test"))))
    (testing "the Message-ID is a uuid at the from domain"
      (is (re-matches #"<[0-9a-f-]{36}@example.test>"
                      (header text "Message-ID"))))))

(deftest render-encoding-test
  (let [text (SUT/render (assoc message
                                :subject "Grüße"
                                :to [{:address "a@example.test"
                                      :name "Ä Person"}]))]
    (testing "a non-ASCII subject is encoded as UTF-8"
      (is (str/starts-with? (header text "Subject") "=?UTF-8?")))
    (testing "a non-ASCII display name is encoded as UTF-8"
      (is (str/starts-with? (header text "To") "=?UTF-8?"))
      (is (str/ends-with? (header text "To") "<a@example.test>")))))

(deftest render-extra-headers-test
  (let [text (SUT/render (assoc message
                                :headers
                                {"X-Campaign" "welcome" :X-Tag "onboarding"}))]
    (is (= "welcome" (header text "X-Campaign")))
    (is (= "onboarding" (header text "X-Tag")))))

(deftest render-body-test
  (testing "text alone is text/plain"
    (is (str/starts-with? (header (SUT/render message) "Content-Type")
                          "text/plain")))
  (testing "text and HTML is multipart/alternative, the text part first"
    (let [text (SUT/render (assoc message :html "<p>Hello</p>"))
          plain (str/index-of text "Content-Type: text/plain")
          html (str/index-of text "Content-Type: text/html")]
      (is (str/starts-with? (header text "Content-Type")
                            "multipart/alternative"))
      (is (some? plain))
      (is (some? html))
      (is (< plain html)))))

(deftest render-from-fallback-test
  (testing "a message without :from takes the client's"
    (let [client {:from {:address "noreply@example.test" :name "Example"}}
          text (SUT/render client (dissoc message :from))]
      (is (= "Example <noreply@example.test>" (header text "From")))))
  (testing "the message's :from wins over the client's"
    (let [client {:from "noreply@example.test"}
          text (SUT/render client message)]
      (is (= "sender@example.test" (header text "From"))))))

(deftest render-invalid-address-test
  (testing "an address that does not parse"
    (let [result (SUT/render (assoc message :to ["not an address"]))]
      (is (error/rejection? result))
      (is (= :smtp/invalid-address (error/kind result)))
      (is (= "not an address" (:address (error/payload result))))
      (is (string? (:message (error/payload result))))))
  (testing "a message with no :to"
    (let [result (SUT/render (dissoc message :to))]
      (is (= :smtp/invalid-address (error/kind result)))
      (is (string? (:message (error/payload result))))))
  (testing "a message with no from anywhere"
    (let [result (SUT/render {} (dissoc message :from))]
      (is (= :smtp/invalid-address (error/kind result)))
      (is (not (contains? (error/payload result) :address))))))

(deftest render-invalid-message-test
  (testing "a message with no :subject"
    (let [result (SUT/render (dissoc message :subject))]
      (is (error/rejection? result))
      (is (= :smtp/invalid-message (error/kind result)))
      (is (= [:subject] (:missing (error/payload result))))))
  (testing "anything else is an anomaly, never a throw"
    (is (error/anomaly? (SUT/render nil)))
    (is (error/anomaly? (SUT/render {:to "a@example.test"})))))
