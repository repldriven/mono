(ns com.repldriven.mono.system.core-test
  (:require
    [com.repldriven.mono.system.core :as SUT]
    [clojure.test :as test :refer [deftest is testing]]))

(deftest namespaced-config-keys
  (testing "Source ns keys in system config are mapped to target ns"
    (let [src {:src/start "start" :src/stop "stop" :src/config {:config "cfg"}}
          tgt {:tgt/start "start" :tgt/stop "stop" :tgt/config {:config "cfg"}}]
      (is (= tgt (#'SUT/nsmap->nsmap src "src" "tgt")))))
  (testing
    "Source and target ns keys args to system config fns are mapped to source ns"
    (let [src {:src/config {:f (fn [{:src/keys [arg1] :as _args}] arg1)}}
          tgt (#'SUT/nsmap->nsmap src "src" "tgt")]
      (is (= "arg1"
             ((get-in src [:src/config :f]) {:src/arg1 "arg1"})
             ((get-in tgt [:tgt/config :f]) {:src/arg1 "arg1"})
             ((get-in tgt [:tgt/config :f]) {:tgt/arg1 "arg1"}))))
    (let [src {:src/config {:f (fn [& args] (namespace (first (ffirst args))))}}
          tgt (#'SUT/nsmap->nsmap src "src" "tgt")]
      (is (= "src"
             ((get-in src [:src/config :f]) {:src/arg1 "unused"})
             ((get-in tgt [:tgt/config :f]) {:src/arg1 "unused"})
             ((get-in tgt [:tgt/config :f]) {:tgt/arg1 "unused"}))))))
