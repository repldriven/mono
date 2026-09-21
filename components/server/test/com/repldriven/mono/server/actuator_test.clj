(ns com.repldriven.mono.server.actuator-test
  (:require
    [com.repldriven.mono.server.interface :as SUT]

    [clojure.test :refer [deftest is testing]]))

(defn- call
  [ctx path]
  (let [routes (into {} (SUT/health-routes ctx))
        handler (get-in routes [path :get :handler])]
    (handler {})))

(deftest health-routes-test
  (testing "liveness is always up"
    (doseq [ctx [{} {:ready-fn (constantly false)}]]
      (let [res (call ctx "/actuator/health/liveness")]
        (is (= 200 (:status res)))
        (is (= "UP" (get-in res [:body :status]))))))
  (testing "readiness follows ready-fn, and is up without one"
    (is (= 200 (:status (call {} "/actuator/health/readiness"))))
    (let [res (call {:ready-fn (constantly false)}
                    "/actuator/health/readiness")]
      (is (= 503 (:status res)))
      (is (= "DOWN" (get-in res [:body :status])))))
  (testing "the aggregate carries both groups and takes readiness' status"
    (let [res (call {:ready-fn (constantly false)} "/actuator/health")]
      (is (= 503 (:status res)))
      (is (= {:status "DOWN"
              :components {:liveness {:status "UP"}
                           :readiness {:status "DOWN"}}}
             (:body res))))
    (is (= 200 (:status (call {} "/actuator/health")))))
  (testing "every response is JSON"
    (doseq [path ["/actuator/health" "/actuator/health/liveness"
                  "/actuator/health/readiness"]]
      (is (= "application/json"
             (get-in (call {} path) [:headers "content-type"]))
          path))))
