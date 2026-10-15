(ns com.repldriven.mono.server.system-test
  (:require
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http-client]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system]]
    [com.repldriven.mono.test-telemetry.interface :as test-telemetry]
    [reitit.http :as http]
    [reitit.ring :as ring]
    [com.repldriven.mono.json.interface :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]])
  (:import
    (java.io ByteArrayInputStream)
    (java.net URI)
    (java.net.http HttpClient
                   HttpRequest
                   HttpRequest$BodyPublishers
                   HttpResponse$BodyHandlers)
    (java.util.concurrent Semaphore)
    (java.util.function Supplier)
    (org.eclipse.jetty.server Server ServerConnector)))

(deftest server-test
  (testing "Server component system configuration and lifecycle"
    (let [handler (fn [_] {:status 200 :body "ok"})]
      (with-test-system [_
                         ["classpath:server/application-test.yml"
                          #(assoc-in %
                            [:system/defs :server :handler]
                            (constantly handler))]]))))

(deftest ephemeral-port-test
  (testing "the adapter's port-0 connector does not share its port"
    (let [handler (fn [_] {:status 200 :body "ok"})]
      (with-test-system
       [sys
        ["classpath:server/application-test.yml"
         #(assoc-in % [:system/defs :server :handler] (constantly handler))]]
       (let [^Server jetty (system/instance sys
                                            [:server
                                             :jetty-adapter])
             ^ServerConnector connector (first (.getConnectors jetty))]
         (is (zero? (.getPort connector)))
         (is (false? (.getReuseAddress connector)))
         (is (pos? (.getLocalPort connector))))))))

(deftest interceptors-test
  (testing "Ring interceptors MUST be inserted"
    (let [data {:got "me" :this "time"}
          handler (fn [req] {:status 200 :body (select-keys req (keys data))})
          routes (fn [ctx] ["/api" {:interceptors (:interceptors ctx)}
                            ["/interceptors" {:get {:handler handler}}]])
          app (fn [ctx]
                (http/ring-handler (http/router (routes ctx)
                                                server/standard-router-data)
                                   (ring/create-default-handler)
                                   server/standard-executor))]
      (with-test-system [sys
                         ["classpath:server/application-test.yml"
                          #(assoc-in % [:system/defs :server :handler] app)]]
                        (let [jetty (system/instance sys
                                                     [:server :jetty-adapter])
                              base-url (server/http-local-url jetty)
                              url (str base-url "/api/interceptors")
                              res (http-client/request {:url url :method :get})
                              body (http-client/res->body res)]
                          (is (= body {"got" "me" "this" "time"})))))))

(deftest coercion-error-test
  (testing "Coercion errors return structured responses"
    (let [routes (fn [_ctx]
                   ["/api"
                    ["/validate"
                     {:post {:parameters {:body [:map [:name string?]]}
                             :responses {200 {:body [:map [:greeting string?]]}}
                             :handler
                             (fn [_] {:status 200 :body {:greeting "hello"}})}}]
                    ["/nested"
                     {:post {:parameters {:body [:map [:tags [:set :int]]]}
                             :responses {200 {:body [:map [:ok boolean?]]}}
                             :handler (fn [_] {:status 200 :body {:ok true}})}}]
                    ["/bad-response"
                     {:post {:parameters {:body [:map [:name string?]]}
                             :responses {200 {:body [:map [:greeting string?]]}}
                             :handler (fn [_]
                                        {:status 200 :body {:wrong "key"}})}}]])
          app (fn [ctx]
                (http/ring-handler (http/router (routes ctx)
                                                server/standard-router-data)
                                   (ring/create-default-handler)
                                   server/standard-executor))]
      (with-test-system
       [sys
        ["classpath:server/application-test.yml"
         #(assoc-in % [:system/defs :server :handler] app)]]
       (let [jetty (system/instance sys [:server :jetty-adapter])
             base-url (server/http-local-url jetty)
             post! (fn [path body]
                     (http-client/request {:method :post
                                           :url (str base-url path)
                                           :headers {"Content-Type"
                                                     "application/json"}
                                           :body (json/write-str body)}))]
         (testing "Valid request returns 200"
           (let [res (post! "/api/validate" {"name" "Alice"})]
             (is (= 200 (:status res)))))
         (testing "Invalid request body returns 400 with error type"
           (let [res (post! "/api/validate" {})
                 body (http-client/res->body res)]
             (is (= 400 (:status res)))
             (is (= "REJECTED" (get body "title")))
             (is (= "server/bad-request" (get body "type")))
             (is (contains? body "detail"))))
         (testing "A set-valued schema sent an array stays a 400"
           ;; humanize throws on this one: the error's :in path carries
           ;; the offending element against a vector value. Without the
           ;; fallback in explain->detail, a client's bad body would
           ;; raise inside the coercion handler and return a 500.
           (let [res (post! "/api/nested" {"tags" ["not-an-int"]})
                 body (http-client/res->body res)]
             (is (= 400 (:status res)))
             (is (= "server/bad-request" (get body "type")))
             (is (re-find #":in \[:tags" (get body "detail")))
             (is (re-find #"not-an-int" (get body "detail")))))
         (testing "Invalid response body returns 500 with error type"
           (let [res (post! "/api/bad-response" {"name" "Alice"})
                 body (http-client/res->body res)]
             (is (= 500 (:status res)))
             (is (= "FAILED" (get body "title")))
             (is (= "server/bad-response" (get body "type")))
             (is (contains? body "detail")))))))))

(defn- header
  [res k]
  (some (fn [[hk v]] (when (= k (str/lower-case (name hk))) v)) (:headers res)))

(deftest max-in-flight-test
  (testing "a request beyond max-in-flight is turned away, a probe is not"
    (let [entered (promise)
          release (promise)
          handler (fn [req]
                    (if (str/starts-with? (:uri req) "/actuator/")
                      {:status 200 :body "UP"}
                      (do (deliver entered true)
                          (deref release 10000 nil)
                          {:status 200 :body "ok"})))]
      (with-test-system
       [sys
        ["classpath:server/application-test.yml"
         #(-> %
              (assoc-in [:system/defs :server :handler] (constantly handler))
              (assoc-in [:system/defs :server :jetty-adapter :system/config
                         :max-in-flight]
                        1))]]
       (let [base (server/http-local-url (system/instance sys
                                                          [:server
                                                           :jetty-adapter]))
             held (future (http-client/request {:url (str base "/slow")
                                                :method :get}))
             _ (deref entered 10000 nil)
             shed (http-client/request {:url (str base "/slow") :method :get})
             probe (http-client/request {:url (str base
                                                   "/actuator/health/liveness")
                                         :method :get})]
         (is (= 503 (:status shed)))
         (is (= "1" (header shed "retry-after")))
         (is (= "server/overloaded" (get (http-client/res->body shed) "type")))
         (is (= 200 (:status probe)))
         (deliver release true)
         (is (= 200 (:status (deref held 10000 nil)))))))))

(deftest rejected-counter-test
  (testing "a request turned away is counted, with its method"
    (let [entered (promise)
          release (promise)
          handler (fn [_]
                    (deliver entered true)
                    (deref release 10000 nil)
                    {:status 200 :body "ok"})]
      (with-test-system
       [sys
        ["classpath:server/shed-test.yml"
         #(assoc-in % [:system/defs :server :handler] (constantly handler))]]
       (let [base (server/http-local-url (system/instance sys
                                                          [:server
                                                           :jetty-adapter]))
             otel (system/instance sys [:telemetry :otel-sdk])
             held (future (http-client/request {:url (str base "/slow")
                                                :method :get}))
             _ (deref entered 10000 nil)
             shed (http-client/request {:url (str base "/slow") :method :post})]
         (is (= 503 (:status shed)))
         (is (= 1
                (test-telemetry/counter-value otel
                                              "mono.server.request.rejected"
                                              {"http.request.method" "POST"
                                               "error.type"
                                               "server/overloaded"})))
         (deliver release true)
         (is (= 200 (:status (deref held 10000 nil)))))))))

(deftest invalid-max-in-flight-test
  (testing "options the limiter refuses come back as its rejection"
    (let [result (server/wrap-max-in-flight (constantly {:status 200})
                                            {:min 10 :initial 5})]
      (is (= :concurrency-limit/invalid-options (error/kind result))))))

(def ^:private max-request-bytes 64)

(defn- json-of-bytes
  [n]
  (str "{\"name\":\"" (apply str (repeat (- n 11) "a")) "\"}"))

(defn- echo-app
  [handled]
  (fn [_ctx]
    (http/ring-handler (http/router ["/api/echo"
                                     {:post {:handler
                                             (fn [req]
                                               (swap! handled inc)
                                               {:status 200
                                                :body (:body-params req)})}}]
                                    server/standard-router-data)
                       (ring/create-default-handler)
                       server/standard-executor)))

(defn- post-json
  [base body]
  (http-client/request {:method :post
                        :url (str base "/api/echo")
                        :headers {"Content-Type" "application/json"}
                        :body body}))

(defn- post-chunked
  [base body]
  (let [bytes (.getBytes ^String body "UTF-8")
        request (-> (HttpRequest/newBuilder (URI. (str base "/api/echo")))
                    (.header "Content-Type" "application/json")
                    (.POST (HttpRequest$BodyPublishers/ofInputStream
                            (reify
                             Supplier
                               (get [_] (ByteArrayInputStream. bytes)))))
                    (.build))
        response (.send (HttpClient/newHttpClient)
                        request
                        (HttpResponse$BodyHandlers/ofString))]
    {:status (.statusCode response) :body (json/read-str (.body response))}))

(defn- with-max-request-bytes
  [n]
  (fn [defs]
    (assoc-in defs
     [:system/defs :server :jetty-adapter :system/config :max-request-bytes]
     n)))

(deftest max-request-bytes-test
  (testing "a body over max-request-bytes is a 413, a body at it is not"
    (let [handled (atom 0)]
      (with-test-system
       [sys
        ["classpath:server/application-test.yml"
         #(-> %
              (assoc-in [:system/defs :server :handler] (echo-app handled))
              ((with-max-request-bytes max-request-bytes)))]]
       (let [base (server/http-local-url (system/instance sys
                                                          [:server
                                                           :jetty-adapter]))]
         (testing "a Content-Length over the cap is refused before the handler"
           (let [res (post-json base (json-of-bytes (inc max-request-bytes)))
                 body (http-client/res->body res)]
             (is (= 413 (:status res)))
             (is (= "server/request-too-large" (get body "type")))
             (is (= 413 (get body "status")))
             (is (= max-request-bytes (get body "max-request-bytes")))
             (is (string? (get body "detail")))
             (is (zero? @handled))))
         (testing "a chunked body that grows past the cap is refused"
           (let [{:keys [status body]} (post-chunked base (json-of-bytes 1000))]
             (is (= 413 status))
             (is (= "server/request-too-large" (get body "type")))
             (is (= max-request-bytes (get body "max-request-bytes")))
             (is (zero? @handled))))
         (testing "a body of exactly the cap is accepted"
           (let [res (post-json base (json-of-bytes max-request-bytes))]
             (is (= 200 (:status res)))
             (is (= 1 @handled))))))))
  (testing "with no max-request-bytes, a large body is accepted"
    (let [handled (atom 0)]
      (with-test-system
       [sys
        ["classpath:server/application-test.yml"
         #(assoc-in % [:system/defs :server :handler] (echo-app handled))]]
       (let [base (server/http-local-url (system/instance sys
                                                          [:server
                                                           :jetty-adapter]))]
         (is (= 200 (:status (post-json base (json-of-bytes 100000)))))
         (is (= 200 (:status (post-chunked base (json-of-bytes 100000))))))))))

(deftest invalid-max-request-bytes-test
  (doseq [n [0 -1 1.5 "64"]]
    (testing (str "refuses max-request-bytes " (pr-str n) " at start")
      (system/with-system [sys
                           (error/nom->
                            (env/config "classpath:server/application-test.yml"
                                        :test)
                            system/defs
                            ((fn [defs]
                               (assoc-in defs
                                [:system/defs :server :handler]
                                (constantly (constantly {:status 200})))))
                            ((with-max-request-bytes n))
                            system/start)]
        (is (error/anomaly? sys))))))

(def ^:private store-concurrency 4)

(def ^:private store-latency-ms 20)

(defn- store-handler
  [^Semaphore store]
  (fn [req]
    (if (str/starts-with? (:uri req) "/actuator/")
      {:status 200 :body "UP"}
      (do (.acquire store)
          (try (Thread/sleep (long store-latency-ms))
               (finally (.release store)))
          {:status 200 :body "ok"}))))

(defn- gauge
  [otel gauge-name]
  (test-telemetry/gauge-value otel gauge-name {}))

(def ^:private overload-clients 64)

(def ^:private dynamic-range {:initial 2 :max 16})

(def ^:private poll-ms 50)

(defn- await-capacity
  [otel]
  (loop [tries 200]
    (let [capacity (gauge otel "mono.server.request.capacity")]
      (if (or (zero? tries) (some? capacity))
        capacity
        (do (Thread/sleep (long poll-ms)) (recur (dec tries)))))))

(defn- sample-limit
  [otel n]
  (vec (repeatedly n
                   (fn []
                     (Thread/sleep (long poll-ms))
                     (gauge otel "mono.server.request.limit")))))

(deftest dynamic-max-in-flight-test
  (testing "a dynamic limit stays within its range under overload"
    (with-test-system
     [sys
      ["classpath:server/shed-test.yml"
       #(-> %
            (assoc-in [:system/defs :server :handler]
                      (constantly (store-handler (Semaphore.
                                                  (int store-concurrency)))))
            (assoc-in [:system/defs :server :jetty-adapter :system/config
                       :max-in-flight]
                      (merge dynamic-range {:window-size 10 :window-ms 100})))]]
     (let [base (server/http-local-url (system/instance sys
                                                        [:server
                                                         :jetty-adapter]))
           otel (system/instance sys [:telemetry :otel-sdk])
           stop (promise)
           clients (doall (repeatedly overload-clients
                                      (fn []
                                        (future (while (not (realized? stop))
                                                  (http-client/request
                                                   {:url (str base "/work")
                                                    :method :get}))))))
           capacity (await-capacity otel)
           limits (sample-limit otel 20)
           probe (http-client/request {:url (str base
                                                 "/actuator/health/liveness")
                                       :method :get})]
       (deliver stop true)
       (run! deref clients)
       (testing "the limit and capacity are published"
         (is (pos? capacity))
         (is (every? some? limits)))
       (testing "the limit never leaves initial and max"
         (let [{lo :initial hi :max} dynamic-range]
           (is (every? (fn [limit] (<= lo limit hi)) limits))))
       (testing "a probe is answered under load"
         (is (= 200 (:status probe))))))))
