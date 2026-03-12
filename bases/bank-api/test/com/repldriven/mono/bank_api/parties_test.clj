(ns com.repldriven.mono.bank-api.parties-test
  (:require

    [com.repldriven.mono.bank-api.api :as api]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as util]

    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing]]))

(def ^:dynamic *base-url* "http://localhost:{PORT}")

(def ^:private test-party
  {:organization-id "org_test_api"
   :party-id "pty_01TEST123"
   :type :person
   :display-name "Jane Doe"
   :status :pending
   :created-at 1700000000000
   :updated-at 1700000000000})

(defn- command-processor
  "Simulates Processor — subscribes to parties-command
  messages and replies with a fixed party payload."
  [sys party]
  (let [bus (system/instance sys [:message-bus :bus])
        schemas (system/instance sys [:avro :serde])
        payload (avro/serialize (get schemas "party") party)]
    (message-bus/subscribe
     bus
     :parties-command
     (fn [data]
       (message-bus/send
        bus
        :parties-command-response
        (command/command-response data
                                  {:status "ACCEPTED"
                                   :payload payload}))))
    {:stop (fn [] (message-bus/unsubscribe bus :parties-command))}))

(defn- create-party-request
  []
  (http/request {:method :post
                 :url (str *base-url* "/v1/parties")
                 :headers {"Content-Type" "application/json"
                           "Idempotency-Key" (str (util/uuidv7))}
                 :body (json/write-str
                        {"type" "PERSON"
                         "display-name" "Jane Doe"
                         "given-name" "Jane"
                         "family-name" "Doe"
                         "date-of-birth" 19900115
                         "nationality" "GB"
                         "national-identifier"
                         {"type" "NATIONAL_INSURANCE"
                          "value" "TN000001A"
                          "issuing-country" "GBR"}})}))

(deftest create-party-test
  (with-test-system
   [sys
    ["classpath:bank-api/application-test.yml"
     #(assoc-in % [:system/defs :server :handler] api/app)]]
   (let [jetty (system/instance sys [:server :jetty-adapter])
         {:keys [stop]} (command-processor sys test-party)]
     (binding [*base-url* (server/http-local-url jetty)]
       (testing "POST /v1/parties sends create-party command"
         (nom-test> [res (create-party-request)
                     _ (is (= 200 (:status res)))
                     body (http/res->edn res)
                     _ (is (= "pty_01TEST123" (:party-id body)))
                     _ (is (= "person" (name (:type body))))
                     _ (is (= "Jane Doe" (:display-name body)))])))
     (stop))))
