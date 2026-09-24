(ns com.repldriven.mono.testcontainers.container
  (:require
    [clojure.string :as str]))

(defn- ->integer-array
  [ports]
  (into-array Integer (map #(Integer/valueOf (int %)) ports)))

(defn reuse?
  "Whether a component's `reuse` config asks for its container to be
  kept and found again by the next boot: a literal `true`, or the
  string shape `!env TESTCONTAINERS_REUSE_ENABLE` produces, since an
  env var cannot carry a boolean. The library honours the request
  only under that same variable, so a rig reads it from there."
  [{:keys [reuse]}]
  (cond (boolean? reuse)
        reuse

        (string? reuse)
        (contains? #{"true" "1" "yes"} (str/lower-case reuse))

        :else
        false))

;; Reuse finds only a container that has finished starting, so boots
;; racing in parallel would each create one and the rest sit idle for
;; good. Reusable starts take this lock, so the first completes before
;; the next looks; starts that are not reusable stay parallel.
(def ^:private reusable-start-lock (Object.))

(defn- start-container!
  [container reuse?]
  (if reuse?
    (locking reusable-start-lock (.start container))
    (.start container)))

(defn start!
  "Starts a testcontainer and returns a map of the container
  instance and its mapped ports. With `:reuse?` the container is
  marked reusable, so a later boot with the same configuration finds
  it running rather than starting another, and `stop!` leaves it."
  ([container exposed-ports] (start! container exposed-ports nil))
  ([container exposed-ports {:keys [reuse?]}]
   (.withExposedPorts container (->integer-array exposed-ports))
   (when reuse? (.withReuse container true))
   (start-container! container reuse?)
   {:container container
    :reused? (boolean reuse?)
    :mapped-ports (into {}
                        (map (fn [p] [p (.getMappedPort container (int p))]))
                        exposed-ports)}))

(defn stop!
  "Stops a testcontainer from a map returned by start!. A reusable
  one is left running: the library stops it when asked, which would
  defeat the reuse."
  [{:keys [container reused?]}]
  (when (and container (not reused?)) (.stop container)))
