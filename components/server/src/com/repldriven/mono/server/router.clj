(ns com.repldriven.mono.server.router
  (:require
    [muuntaja.core :as m]
    [reitit.coercion.malli]
    [reitit.dev.pretty :as pretty]
    [reitit.http.coercion :as coercion]
    [reitit.http.interceptors.exception :as exception]
    [reitit.http.interceptors.muuntaja :as muuntaja]
    [reitit.http.interceptors.parameters :as parameters]
    [reitit.interceptor.sieppari :as sieppari]
    [reitit.openapi :as openapi]))

(def muuntaja-instance
  "Muuntaja instance configured for JSON only with keyword keys."
  (m/create (-> m/default-options
                (update :formats select-keys ["application/json"])
                (assoc-in [:formats "application/json" :decoder-opts
                           :decode-key-fn]
                          keyword))))

(def default-exception-handlers
  "Default exception handlers for Reitit router.

  Handles Malli coercion failures for both request (400) and
  response (500), returning RFC 9457-shaped error bodies. Falls
  back to reitit's default handler for all other exceptions."
  (merge exception/default-handlers
         {:reitit.coercion/request-coercion
          (fn [ex _req]
            {:status 400
             :body {:title "REJECTED"
                    :type "mono/bad-request"
                    :status 400
                    :detail (str (:humanized (ex-data ex)))}})
          :reitit.coercion/response-coercion
          (fn [ex _req]
            {:status 500
             :body {:title "FAILED"
                    :type "mono/bad-response"
                    :status 500
                    :detail (str (:humanized (ex-data ex)))}})}))

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
           :interceptors [;; openapi feature
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
