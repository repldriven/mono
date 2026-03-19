(ns com.repldriven.mono.cli.core
  (:require
    [com.repldriven.mono.log.interface :as log]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.cli :as cli]))

(def cli-options
  [["-c" "--config-file FILENAME" "Configuration filename" :default "env.edn"
    :parse-fn str :validate [#(some? (io/file %)) "Missing configuration file"]]
   ["-p" "--profile [default|dev|test]" "Profile name" :default "default"
    :parse-fn str :validate
    [#(contains? #{"default" "dev" "test"} %) "Bad profile name"]]
   ["-h" "--help"]])

(defn usage
  [program-name options-summary]
  (string/join \newline
               [program-name "" "Usage: program-name [options]" "" "Options:"
                options-summary ""]))

(defn error-msg
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with an error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [program-name args]
  (let [{:keys [options errors summary]} (cli/parse-opts args cli-options)]
    (cond (:help options)
          {:exit-message (usage program-name summary)
           :ok? true}
          errors
          {:exit-message (error-msg errors)}
          :else
          {:options options})))

(defn exit
  [ok? msg]
  (if ok? (log/info msg) (log/error msg))
  (System/exit (if ok? 0 1)))


(comment
  {:a "1" :b "2"})
