(ns com.repldriven.mono.bank-api.export-spec
  (:require
    [com.repldriven.mono.bank-api.api :as api]

    [clj-yaml.core :as yaml]
    [clojure.data.json :as json]
    [clojure.java.io :as io]))

(defn -main
  [& [out-path]]
  (let [path (or out-path "docs/openapi.yaml")
        handler (api/app {:interceptors []})
        body (-> {:request-method :get :uri "/openapi.json"}
                 handler
                 :body)
        spec (json/read-str (slurp body) :key-fn keyword)]
    (io/make-parents path)
    (spit path
          (yaml/generate-string spec
                                :dumper-options
                                {:flow-style :block}))
    (println "Wrote" path)))
