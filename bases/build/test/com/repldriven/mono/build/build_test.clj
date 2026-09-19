(ns ^:eftest/synchronized com.repldriven.mono.build.build-test
  (:require
    [com.repldriven.mono.build.build :as SUT]
    [clojure.test :refer [deftest is testing]]
    [clojure.tools.build.api :as b]))

(defn- uber-with-stubs
  "Run the build with every tools.build call stubbed, returning the
  result and the calls it made, in order."
  [opts]
  (let [calls (atom [])
        record (fn [op] (fn [args] (swap! calls conj [op args]) args))]
    (with-redefs [b/git-count-revs (constantly "42")
                  b/create-basis (constantly ::basis)
                  b/delete (record :delete)
                  b/compile-clj (record :compile-clj)
                  b/uber (record :uber)]
      {:result (SUT/uber (merge {:lib 'com.example/service
                                 :main 'com.example.main}
                                opts))
       :calls @calls})))

(deftest uber-file-test
  (testing "the git revision count is the patch version"
    (is (= "target/service-0.0.42.jar"
           (:uber-file (:result (uber-with-stubs {}))))))
  (testing "major-minor-version leads the version"
    (is (= "target/service-1.2.42.jar"
           (:uber-file (:result (uber-with-stubs {:major-minor-version
                                                  "1.2"}))))))
  (testing "a snapshot replaces the patch version, git count or not"
    (is (= "target/service-2.0.999-SNAPSHOT.jar"
           (:uber-file (:result (uber-with-stubs {:major-minor-version "2.0"
                                                  :snapshot true})))))))

(deftest uber-build-test
  (let [{:keys [calls]} (uber-with-stubs {})
        by-op (into {} calls)]
    (testing "target is cleaned, then compiled, then packaged"
      (is (= [:delete :compile-clj :uber] (mapv first calls))))
    (testing "only the main namespace is named for compilation"
      (is (= ['com.example.main] (:ns-compile (:compile-clj by-op)))))
    (testing "the uberjar is executable and merges what a merge needs"
      (let [{:keys [main class-dir conflict-handlers]} (:uber by-op)]
        (is (= 'com.example.main main))
        (is (= "target/classes" class-dir))
        (is (= :data-readers (get conflict-handlers "^data_readers.clj[cs]?$")))
        (is (= :append (get conflict-handlers "^META-INF/services/.*")))
        (is (= :ignore (:default conflict-handlers)))))
    (testing "the options it was given come back"
      (is (= 'com.example/service (:lib (:result (uber-with-stubs {}))))))))
