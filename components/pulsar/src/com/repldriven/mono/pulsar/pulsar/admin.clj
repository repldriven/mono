(ns com.repldriven.mono.pulsar.pulsar.admin
  (:require
    [com.repldriven.mono.error.interface :refer [try-nom try-nom-ex]]
    [com.repldriven.mono.log.interface :as log]
    [clojure.java.data.builder :as builder]
    [clojure.string :as string])
  (:import
    (org.apache.pulsar.client.admin PulsarAdmin
                                    PulsarAdminBuilder
                                    PulsarAdminException)))

(defn create
  ^PulsarAdmin [{:keys [service-http-url]}]
  (log/info "Creating Pulsar admin: " service-http-url)
  (try-nom-ex :pulsar/admin-create PulsarAdminException
              "Failed to create Pulsar admin"
              (builder/to-java PulsarAdmin
                               (PulsarAdmin/builder)
                               {:serviceHttpUrl service-http-url}
                               {:builder-class PulsarAdminBuilder})))

(defn close
  [^PulsarAdmin admin]
  (log/info "Closing Pulsar admin connection")
  (try-nom-ex :pulsar/admin-close PulsarAdminException
              "Failed to close Pulsar admin connection" (.close admin)))

(defn namespace-url
  "Get the admin URL for a Pulsar namespace."
  [^PulsarAdmin admin tenant namespace]
  (try-nom
   :pulsar/admin-namespace-url
   "Failed to get Pulsar admin namespace URL"
   (let [service-url (.getServiceUrl admin)]
     (string/join "/" [service-url "admin/v2/namespaces" tenant namespace]))))
