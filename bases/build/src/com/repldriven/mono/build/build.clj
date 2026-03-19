(ns com.repldriven.mono.build.build
  (:require
    [org.corfield.build :as bb]
    [clojure.tools.build.api :as b]))

(defn uber
  "Build a polylith project uberjar"
  [opts]
  (let [major-minor-version (get opts :major-minor-version "0.0")
        patch-version
        (if (:snapshot opts) "999-SNAPSHOT" (b/git-count-revs nil))
        version (format "%s.%s" major-minor-version patch-version)]
    (-> opts
        (assoc :version version
               :transitive true
               :conflict-handlers
               {"^data_readers.clj[cs]?$" :data-readers
                "^META-INF/services/.*" :append
                "(?i)^(META-INF/)?(COPYRIGHT|NOTICE|LICENSE)(\\.(txt|md))?$"
                :ignore
                :default :ignore})
        (bb/clean)
        (bb/uber))))
