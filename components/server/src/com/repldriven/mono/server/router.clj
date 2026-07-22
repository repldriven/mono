(ns com.repldriven.mono.server.router
  (:require
    [com.repldriven.mono.log.interface :as log]

    [malli.error :as me]
    [muuntaja.core :as m]
    [reitit.coercion.malli]
    [reitit.dev.pretty :as pretty]
    [reitit.http.coercion :as coercion]
    [reitit.http.interceptors.exception :as exception]
    [reitit.http.interceptors.muuntaja :as muuntaja]
    [reitit.http.interceptors.parameters :as parameters]
    [reitit.interceptor.sieppari :as sieppari]
    [reitit.openapi :as openapi]
    [reitit.ring :as ring]

    [clojure.string :as str]))

(def muuntaja-instance
  "Muuntaja instance configured for JSON only with keyword keys."
  (m/create (-> m/default-options
                (update :formats select-keys ["application/json"])
                (assoc-in [:formats "application/json" :decoder-opts
                           :decode-key-fn]
                          keyword))))

(defn- explain->detail
  "Turns a reitit coercion `ex-data` map into a `:detail` string.
  Prefers `malli.error/humanize`, but that throws
  `IllegalArgumentException: Key must be integer` when an error's
  `:in` path contains a non-integer segment (e.g. a bad element
  inside a `:set`). On that failure, fall back to a compact list of
  `{:in :value :schema}` fragments derived from `:errors`."
  [{:keys [schema value errors]}]
  (try (pr-str (me/humanize {:schema schema :value value :errors errors}))
       (catch Exception _
         (pr-str (mapv (fn [e]
                         {:in (:in e)
                          :value (:value e)
                          :schema (pr-str (:schema e))})
                       errors)))))

(def default-exception-handlers
  "Default exception handlers for Reitit router. Every handler returns an
  RFC 9457-shaped body. Only the catch-all `::exception/default` logs — the
  known client-triggered cases (bad JSON, failed coercion) are expected and
  would flood the logs if each one produced a stack trace."
  (merge
   exception/default-handlers
   {::exception/default (fn [^Throwable e {:keys [request-method uri]}]
                          (log/error e
                                     (str (some-> request-method
                                                  name
                                                  .toUpperCase)
                                          " " uri
                                          " -> " (.getName (class e))
                                          ": " (ex-message e)))
                          {:status 500
                           :body {:title "FAILED"
                                  :type "server/internal-error"
                                  :status 500
                                  :detail (or (ex-message e)
                                              (.getSimpleName (class e)))}})
    :muuntaja/decode (fn [_e _req]
                       {:status 400
                        :body {:title "REJECTED"
                               :type "mono/malformed-body"
                               :status 400
                               :detail "Malformed JSON request body"}})
    :reitit.coercion/request-coercion (fn [ex _req]
                                        {:status 400
                                         :body {:title "REJECTED"
                                                :type "mono/bad-request"
                                                :status 400
                                                :detail (explain->detail
                                                         (ex-data ex))}})
    :reitit.coercion/response-coercion (fn [ex _req]
                                         {:status 500
                                          :body {:title "FAILED"
                                                 :type "mono/bad-response"
                                                 :status 500
                                                 :detail (explain->detail
                                                          (ex-data ex))}})}))

(def ^:private request-log
  {:name ::request-log
   :enter (fn [ctx] (assoc-in ctx [:request ::start-ns] (System/nanoTime)))
   :leave (fn [ctx]
            (let [{:keys [request-method uri ::start-ns]} (:request ctx)
                  status (get-in ctx [:response :status])
                  ms (when start-ns (/ (- (System/nanoTime) start-ns) 1e6))]
              (log/info (str (.toUpperCase (name request-method))
                             " "
                             uri
                             " "
                             status
                             (when ms (format " %.1fms" ms)))))
            ctx)})

(defn router-data
  "Build Reitit router data, merging the given exception handlers with defaults.

  Args:
  - exception-handlers: Map of exception type -> handler fn, merged with
    default-exception-handlers. Pass {} or omit for defaults only.

  Returns router data map suitable for reitit.http/router."
  ([] (router-data {}))
  ([exception-handlers]
   {:exception pretty/exception
    :syntax :bracket
    :data {:coercion reitit.coercion.malli/coercion
           :muuntaja muuntaja-instance
           :interceptors [;; request logging
                          request-log
                          ;; openapi feature
                          openapi/openapi-feature
                          ;; query-params & form-params
                          (parameters/parameters-interceptor)
                          ;; content-negotiation
                          (muuntaja/format-negotiate-interceptor)
                          ;; encoding response body
                          (muuntaja/format-response-interceptor)
                          ;; exception handling
                          (exception/exception-interceptor
                           (merge default-exception-handlers
                                  exception-handlers))
                          ;; decoding request body
                          (muuntaja/format-request-interceptor)
                          ;; coercing response bodys
                          (coercion/coerce-response-interceptor)
                          ;; coercing request parameters
                          (coercion/coerce-request-interceptor)]}}))

(def standard-router-data
  "Default Reitit router configuration with Malli coercion, Muuntaja, and OpenAPI support."
  (router-data))

(def standard-executor
  "Default Sieppari executor for Reitit ring handler."
  {:executor sieppari/executor})

(defn- json-response
  [status title type detail]
  {:status status
   :headers {"Content-Type" "application/json"}
   :body (str "{\"title\":"
              (pr-str title)
              ",\"type\":"
              (pr-str type)
              ",\"status\":"
              status
              ",\"detail\":"
              (pr-str detail)
              "}")})

(defn standard-default-handler
  "Default handler for unmatched routes and methods.
  Returns JSON error responses for 404, 405, and 406."
  []
  (ring/create-default-handler
   {:not-found
    (constantly (json-response 404 "NOT_FOUND"
                               "server/not-found"
                               "Route not found"))
    :method-not-allowed
    (fn [request]
      (let [methods (some->> request
                             :reitit.core/match
                             :result
                             keys
                             (map (fn [m]
                                    (.toUpperCase (name m))))
                             (str/join ", "))]
        (cond-> (json-response 405 "METHOD_NOT_ALLOWED"
                               "server/method-not-allowed"
                               "Method not allowed")
                methods
                (assoc-in [:headers "Allow"] methods))))
    :not-acceptable
    (constantly (json-response 406 "NOT_ACCEPTABLE"
                               "server/not-acceptable"
                               "Not acceptable"))}))
