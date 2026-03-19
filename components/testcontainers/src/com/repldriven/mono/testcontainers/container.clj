(ns com.repldriven.mono.testcontainers.container)

(defn- ->integer-array
  [ports]
  (into-array Integer (map #(Integer/valueOf (int %)) ports)))

(defn start!
  "Starts a testcontainer and returns a map of the container
  instance and its mapped ports."
  [container exposed-ports]
  (doto container (.withExposedPorts (->integer-array exposed-ports)) (.start))
  {:container container
   :mapped-ports (into {}
                       (map (fn [p] [p (.getMappedPort container (int p))]))
                       exposed-ports)})

(defn stop!
  "Stops a testcontainer from a map returned by start!."
  [{:keys [container]}]
  (when container (.stop container)))
