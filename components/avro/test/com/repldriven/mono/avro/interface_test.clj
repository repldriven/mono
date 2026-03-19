(ns com.repldriven.mono.avro.interface-test
  (:require
    [com.repldriven.mono.avro.interface :as SUT]
    [clojure.test :refer [deftest is testing]]))

(def pet-schema-json
  "{\"type\":\"record\",\"name\":\"Pet\",\"namespace\":\"com.example\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"species\",\"type\":\"string\"},{\"name\":\"age_months\",\"type\":[\"null\",\"int\"],\"default\":null}]}")

(deftest json->schema-test
  (testing "Convert JSON string to Avro schema"
    (let [schema (SUT/json->schema pet-schema-json)] (is (some? schema)))))

(deftest serialize-deserialize-test
  (testing "Serialize and deserialize Avro data"
    (let [schema (SUT/json->schema pet-schema-json)
          data {:name "Whiskers" :species "cat" :age-months 24}
          serialized (SUT/serialize schema data)
          deserialized (SUT/deserialize-same schema serialized)]
      (is (bytes? serialized))
      (is (pos? (alength serialized)))
      (is (= "Whiskers" (get deserialized :name)))
      (is (= "cat" (get deserialized :species)))
      (is (= 24 (get deserialized :age-months)))))
  (testing "Serialize data with null optional field"
    (let [schema (SUT/json->schema pet-schema-json)
          data {:name "Rex" :species "dog" :age-months nil}
          serialized (SUT/serialize schema data)
          deserialized (SUT/deserialize-same schema serialized)]
      (is (= "Rex" (get deserialized :name)))
      (is (= "dog" (get deserialized :species)))
      (is (nil? (get deserialized :age-months))))))
