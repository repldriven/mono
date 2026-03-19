(ns com.repldriven.mono.utility.string-test
  (:require
    [com.repldriven.mono.utility.string :as SUT]
    [clojure.test :refer [deftest is testing]])
  (:import
    (java.io InputStream)))

(deftest string->stream-test
  (testing "Convert string to InputStream"
    (let [s "hello world"
          stream (SUT/string->stream s)]
      (is (instance? InputStream stream))
      (is (= s (slurp stream))))))

(deftest resolve-source-test
  (testing "Resolve classpath: prefix"
    (is (some? (SUT/resolve-source "classpath:test.yml"))))
  (testing "Pass through non-classpath sources"
    (is (= "/path/to/file" (SUT/resolve-source "/path/to/file")))))

(deftest prop-seq->kw-map-test
  (testing "Convert property strings to keyword map"
    (is (= {:key1 "value1" :key2 "value2"}
           (SUT/prop-seq->kw-map ["key1=value1" "key2=value2"])))
    (is (= {:foo "bar"} (SUT/prop-seq->kw-map [" foo = bar "])))))
