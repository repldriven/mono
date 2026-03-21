(ns com.repldriven.mono.pulsar.pulsar.namespaces
  (:require
    [com.repldriven.mono.error.interface :as error :refer [try-nom]]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.log.interface :as log]
    [clojure.data.json :as json]
    [clojure.string :as string])
  (:import
    (org.apache.pulsar.client.admin PulsarAdmin Namespaces)))

(defn- configure
  [^PulsarAdmin admin fully-qualified-namespace-name config]
  (let [namespace-url (string/join "/"
                                   [(.getServiceUrl admin) "admin/v2/namespaces"
                                    fully-qualified-namespace-name])]
    (log/info "Configuring Pulsar namespace:" fully-qualified-namespace-name)
    (doseq [[method settings] config]
      (doseq [[k v] settings]
        (let [url (string/join "/" [namespace-url (name k)])
              body (json/write-str v)
              headers {"Content-Type" "application/json"}
              res (http/request
                   {:method method :url url :headers headers :body body})]
          (when (error/anomaly? res)
            (log/warnf "Failed to configure Pulsar namespace: %s - %s"
                       fully-qualified-namespace-name
                       res)))))))

(defn- create
  [^PulsarAdmin admin fully-qualified-namespace-name & {:keys [config]}]
  (let [^Namespaces namespaces (.namespaces admin)
        tenant-name (first (string/split fully-qualified-namespace-name #"/"))
        namespace-names (.getNamespaces namespaces tenant-name)]
    (when-not (contains? (set namespace-names) fully-qualified-namespace-name)
      (log/info "Creating Pulsar namespace:" fully-qualified-namespace-name)
      (.createNamespace namespaces fully-qualified-namespace-name)
      (when (some? config)
        (configure admin fully-qualified-namespace-name config)))))

(defn create-namespaces
  [{:keys [^PulsarAdmin admin namespaces]}]
  (log/info "Creating Pulsar namespaces:" (map :namespace namespaces))
  (try-nom :pulsar/namespaces-create
           "Failed to create Pulsar namespaces"
           (doseq [{:keys [namespace] :as opts} namespaces]
             (create admin namespace (dissoc opts :namespace)))))
