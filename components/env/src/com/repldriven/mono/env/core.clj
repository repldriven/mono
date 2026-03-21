(ns com.repldriven.mono.env.core
  (:require
    aero.core
    [com.repldriven.mono.env.reader.edn :as reader.edn]
    [com.repldriven.mono.env.reader.yml :as reader.yml]
    [com.repldriven.mono.error.interface :refer [try-nom]]
    [clojure.string :as str]))

(def edn-reader reader.edn/edn-reader)
(def yml-reader reader.yml/yml-reader)

(defn- file-type->keyword
  [source]
  (cond (str/ends-with? source ".edn")
        :edn
        (str/ends-with? source ".yml")
        :yml
        (str/ends-with? source ".yaml")
        :yml
        :else
        (throw (ex-info "Unknown file type" {:source source}))))

(defmulti file-type (fn [source] (type source)))
(defmethod file-type java.net.URL
  [^java.net.URL source]
  (file-type->keyword (.getPath source)))
(defmethod file-type java.lang.String [source] (file-type->keyword source))
(defmethod file-type :default
  [source]
  (throw (ex-info "Cannot detect file type" {:source source})))

(defmulti read-config (fn [source _] (file-type source)))
(defmethod read-config :edn [source profile] (reader.edn/config source profile))
(defmethod read-config :yml [source profile] (reader.yml/config source profile))
(defmethod read-config :default
  [source _]
  (throw (ex-info "Unsupported config file type" {:source source})))

(defn config
  ([]
   (try-nom :env/config
            "Failed to load config"
            (read-config "classpath:application.edn" :default)))
  ([source]
   (try-nom :env/config
            "Failed to load config"
            (read-config source :default)))
  ([source profile]
   (try-nom :env/config
            "Failed to load config"
            (read-config source profile))))

