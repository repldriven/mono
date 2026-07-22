(ns dev.example-api
  (:require
    com.repldriven.mono.testcontainers.interface

    [{{top-ns}}.example-api.main :as main]))

;; before starting the system:
;; * on Mac OS X, start docker (just start-docker),
;; * start repl (just repl),
;; * connect the repl to your IDE and evaluate file
;; after starting the system:
;; * the openapi3 documentation can be viewed at http://localhost:8080
;; NOTE: on a fresh install, it may take several minutes to download
;; required images for FDB, etc

(comment
  (def sys (main/start "classpath:example-api/application-test.yml" :dev))
  (tap> sys)
  (main/stop sys))

;)
