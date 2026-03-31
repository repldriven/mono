(ns com.repldriven.mono.env.reader.yml
  (:require
    [com.repldriven.mono.env.reader.edn :as reader.edn]

    [com.repldriven.mono.utility.interface :as util]

    [clj-yaml.core :as yaml]

    [clojure.java.io :as io]
    [clojure.string :as str]))

(defmulti yml-reader (fn [m] (keyword (get m :tag))))

;; Default - return the value as-is
(defmethod yml-reader :default [m] (:value m))

;; Basic common tag readers
(defmethod yml-reader :!profile
  [{:keys [value]}]
  (symbol (str "#profile " (util/yaml-collections->edn-collections value))))

(defmethod yml-reader :!port [{:keys [value]}] (symbol (str "#port " value)))

(defmethod yml-reader :!include
  [{:keys [value]}]
  (let [key-fn (fn [{:keys [key]}]
                 (if (and (str/starts-with? key "\"") (str/ends-with? key "\""))
                   (subs key 1 (dec (count key)))
                   (keyword key)))]
    (-> value
        io/resource
        io/reader
        (yaml/parse-stream {:key-fn key-fn :unknown-tag-fn yml-reader})
        util/yaml-collections->edn-collections)))

(defmethod yml-reader :!env [{:keys [value]}] (System/getenv (name value)))

(defmethod yml-reader :!keyword [{:keys [value]}] (keyword value))

(defmethod yml-reader :!str [{:keys [value]}] (str "\"" (name value) "\""))

(defmethod yml-reader :!strs [{:keys [value]}] (util/keys->strs value))

(defn- key-fn
  [{:keys [key]}]
  (if (and (str/starts-with? key "\"") (str/ends-with? key "\""))
    (subs key 1 (dec (count key)))
    (keyword key)))

(defn config
  [source profile]
  (-> (util/resolve-source source)
      io/reader
      (yaml/parse-stream {:key-fn key-fn :unknown-tag-fn yml-reader})
      util/yaml-collections->edn-collections
      str
      util/string->stream
      (reader.edn/read-config profile)))
