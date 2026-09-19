(ns com.repldriven.mono.build.build
  (:require
    [clojure.tools.build.api :as b]))

(def ^:private target "target")

(def ^:private class-dir (str target "/classes"))

(def ^:private conflict-handlers
  {"^data_readers.clj[cs]?$" :data-readers
   "^META-INF/services/.*" :append
   "(?i)^(META-INF/)?(COPYRIGHT|NOTICE|LICENSE)(\\.(txt|md))?$" :ignore
   :default :ignore})

(defn- version
  [{:keys [major-minor-version snapshot]}]
  (format "%s.%s"
          (or major-minor-version "0.0")
          (if snapshot "999-SNAPSHOT" (b/git-count-revs nil))))

(defn uber
  "Build an uberjar for a polylith project, at
  `target/<lib>-<version>.jar`. Requires :lib and :main, and takes
  :major-minor-version and :snapshot, which decide the version."
  [{:keys [lib main] :as opts}]
  (let [uber-file (format "%s/%s-%s.jar" target (name lib) (version opts))
        basis (b/create-basis {})]
    (b/delete {:path target})
    ;; Every brick reaches the jar as a :local/root library, but the
    ;; project's own :paths are not libraries and `uber` would leave them
    ;; out, taking the deployment's resources with them
    (b/copy-dir {:src-dirs (:paths basis) :target-dir class-dir})
    (b/compile-clj {:basis basis :class-dir class-dir :ns-compile [main]})
    (b/uber {:basis basis
             :class-dir class-dir
             :uber-file uber-file
             :main main
             :conflict-handlers conflict-handlers})
    (assoc opts :uber-file uber-file)))
