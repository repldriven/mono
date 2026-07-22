(ns com.repldriven.mono.http-client.interface
  "Synchronous and asynchronous HTTP client (http-kit) with
  anomaly-friendly error handling and JSON/EDN body parsing
  helpers. Failures (thrown exceptions, http-kit `:error`
  responses, parse errors) come back as `:http-client/request` or
  `:http-client/body-parse` anomalies."
  (:require
    [com.repldriven.mono.http-client.core :as client]))

(defn request
  "Make a synchronous HTTP request. Returns the response map
  (`{:status :headers :body …}`) or an
  `:http-client/request` anomaly.

  Args:
  - opts: http-kit request options."
  [opts]
  (client/request opts))

(defn request-async
  "Make an asynchronous HTTP request. Returns a promise; callers
  deref and handle errors themselves (no anomaly translation).

  Args:
  - opts: http-kit request options."
  [opts]
  (client/request-async opts))

(defn res->body
  "Extract the response body as a string, or as parsed JSON when
  the response's content-type contains `json`. Passes anomalies
  through. Returns nil for nil response.

  Args:
  - res: a response map or anomaly."
  [res]
  (client/res->body res))

(defn res->edn
  "Like `res->body` but parses JSON with keyword keys.

  Args:
  - res: a response map or anomaly."
  [res]
  (client/res->edn res))
