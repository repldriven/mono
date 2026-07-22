(ns com.repldriven.mono.server.interceptors
  "HTTP-layer interceptors that don't fit elsewhere — generic concerns
  shared across all services (idempotency-key validation, etc.)."
  (:require
    [sieppari.context :as sc]))

(def ^:private idempotency-key-re #"^[A-Za-z0-9_\-]{16,255}$")

(def require-idempotency-key
  "Interceptor that validates the `Idempotency-Key` header is present
  and syntactically well-formed (16-255 URL-safe ASCII chars).

  Returns 400 Bad Request with an RFC-9457 body if the header is
  missing or malformed."
  {:name ::require-idempotency-key
   :enter (fn [ctx]
            (let [key (get-in ctx [:request :headers "idempotency-key"])]
              (cond (nil? key)
                    (sc/terminate ctx
                                  {:status 400
                                   :body {:title "REJECTED"
                                          :type "mono/missing-idempotency-key"
                                          :status 400
                                          :detail
                                          "Missing Idempotency-Key header"}})
                    (not (re-matches idempotency-key-re key))
                    (sc/terminate
                     ctx
                     {:status 400
                      :body
                      {:title "REJECTED"
                       :type "mono/invalid-idempotency-key"
                       :status 400
                       :detail
                       "Idempotency-Key must be 16-255 URL-safe ASCII chars"}})
                    :else
                    ctx)))})
