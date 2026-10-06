(ns com.repldriven.mono.server.system-test
  (:require
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
    (java.util.concurrent Semaphore)
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

(def ^:private poll-ms 50)

(defn- median
  [xs]
  (let [sorted (vec (sort (remove nil? xs)))]
    (when (seq sorted) (nth sorted (quot (count sorted) 2)))))

(defn- await-capacity
  [otel]
  (loop [tries 200]
    (let [capacity (gauge otel "mono.server.request.capacity")]
      (if (or (zero? tries) (and capacity (>= capacity 100)))
        capacity
        (do (Thread/sleep (long poll-ms)) (recur (dec tries)))))))

(defn- sample-limit
  [otel n]
  (vec (repeatedly n
                   (fn []
                     (Thread/sleep (long poll-ms))
                     (gauge otel "mono.server.request.limit")))))

(deftest dynamic-max-in-flight-test
  (testing "a dynamic limit finds a saturated store's knee and publishes it"
    (with-test-system
     [sys
      ["classpath:server/shed-test.yml"
       #(-> %
            (assoc-in [:system/defs :server :handler]
                      (constantly (store-handler (Semaphore.
                                                  (int store-concurrency)))))
            (assoc-in [:system/defs :server :jetty-adapter :system/config
                       :max-in-flight]
                      {:initial 2 :window-size 10 :window-ms 100}))]]
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
           limits (sample-limit otel 60)
           probe (http-client/request {:url (str base
                                                 "/actuator/health/liveness")
                                       :method :get})]
       (deliver stop true)
       (run! deref clients)
       (testing "capacity reads near the store's 200 a second"
         (is (some? capacity))
         (is (<= 100 capacity 260)))
       (testing "the limit rises past initial and holds near the knee of 4"
         (is (every? some? limits))
         (is (<= 4 (median limits) 8)))
       (testing "once the search has settled, the limit does not climb"
         (is (every? (fn [limit] (<= 2 limit 12))
                     (remove nil? (drop (quot (count limits) 2) limits)))))
       (testing "a probe is answered under load"
         (is (= 200 (:status probe))))))))
