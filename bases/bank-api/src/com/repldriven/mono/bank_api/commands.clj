(ns com.repldriven.mono.bank-api.commands
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- get-schema
  [schemas command-name]
  (or (get schemas command-name)
      (error/fail :api/unknown-command
                  {:message "Unknown command" :command command-name})))

(defn- decode-payload
  [schemas response-schema result]
  (if-let [payload (:payload result)]
    (let [schema (get schemas response-schema)]
      (avro/deserialize-same schema payload))
    {}))

(defn send
  [dispatcher request command-name response-schema data]
  (let [schemas (:avro request)
        envelope (command/req->command-request request command-name)
        result (let-nom>
                 [schema (get-schema schemas command-name)
                  payload
                  (avro/serialize schema data)]
                 (command/send dispatcher (assoc envelope :payload payload)))]
    (cond (error/anomaly? result)
          {:status 500
           :body (errors/error-response 500 result)}
          (= "REJECTED" (:status result))
          {:status 422 :body (errors/error-response 422 "REJECTED" result)}
          (= "FAILED" (:status result))
          {:status 500 :body (errors/error-response 500 "FAILED" result)}
          :else
          (let [body (decode-payload schemas response-schema result)]
            (if (error/anomaly? body)
              {:status 500 :body (errors/error-response 500 body)}
              {:status 200 :body body})))))
