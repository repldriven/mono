(ns com.repldriven.mono.auth.interceptors
  (:require
    [com.repldriven.mono.auth.token :as token]

    [com.repldriven.mono.error.interface :as error]

    [sieppari.context :as sc]

    [clojure.string :as str]))

(def default-schemes #{"token" "bearer"})

(def default-claims-key :auth-claims)

(def default-credential-key :credential)

(defn header->token
  "The credential out of an Authorization header value, or nil.

  Accepts any scheme in `schemes`, compared case-insensitively. RealWorld
  sends `Token <jwt>` where most APIs send `Bearer <jwt>`, and a brick that
  only understood one of them would be wrong half the time."
  [header schemes]
  (when (string? header)
    (let [[scheme credential] (str/split (str/trim header) #"\s+" 2)]
      (when (and credential (contains? schemes (str/lower-case scheme)))
        (let [credential (str/trim credential)]
          (when (seq credential) credential))))))

(defn credential-interceptor
  "Interceptor that puts the Authorization credential on the request, its
  scheme stripped.

  Extraction and nothing else: what the credential is — a JWT to verify, a
  session to look up — is for the interceptor after it, which reads one
  key instead of parsing the header again. Sets nothing when the header is
  absent, malformed or of a scheme not accepted, and never rejects."
  ([] (credential-interceptor nil))
  ([opts]
   (let [{:keys [schemes credential-key]} opts
         schemes (or schemes default-schemes)
         credential-key (or credential-key default-credential-key)]
     {:name ::credential
      :enter (fn [ctx]
               (let [header (get-in ctx [:request :headers "authorization"])]
                 (if-some [credential (header->token header schemes)]
                   (assoc-in ctx [:request credential-key] credential)
                   ctx)))})))

(defn- ->signer
  "A signer, from either a signer or something that finds one.

  Interceptors are built once, at routing time, but a signer is a started
  system component that only reaches the request later. Accepting a
  resolver means a caller can pass `:signer` — a keyword is a function of
  the request — instead of having to thread the component through routing."
  [signer request]
  (if (map? signer) signer (signer request)))

(defn token-interceptor
  "Interceptor that verifies a bearer credential and assocs its claims
  onto the request.

  Deliberately does not reject when the header is absent or invalid: the
  claims key is simply not set. Endpoints where authentication is optional
  need exactly that, and the ones that require it say so with
  `require-auth`, which is where the refusal belongs."
  ([signer] (token-interceptor signer nil))
  ([signer opts]
   (let [{:keys [schemes claims-key]} opts
         schemes (or schemes default-schemes)
         claims-key (or claims-key default-claims-key)]
     {:name ::token
      :enter (fn [ctx]
               (let [{:keys [request]} ctx
                     header (get-in request [:headers "authorization"])
                     credential (header->token header schemes)
                     claims (when credential
                              (token/verify (->signer signer request)
                                            credential))]
                 (if (and claims (not (error/anomaly? claims)))
                   (assoc-in ctx [:request claims-key] claims)
                   ctx)))})))

(def default-unauthorized-response
  {:status 401
   :body {:title "UNAUTHORIZED"
          :type "auth/unauthorized"
          :status 401
          :detail "Authentication required"}})

(defn require-auth
  "Interceptor that terminates with an unauthorized response unless
  `token-interceptor` has already set claims.

  The response is a parameter because the body is a property of the API
  being served, not of authentication: this brick's RFC-9457 default suits
  mono's other services, and an API with its own error contract passes its
  own shape rather than being forced to translate ours."
  ([] (require-auth nil nil))
  ([response] (require-auth response nil))
  ([response opts]
   (let [response (or response default-unauthorized-response)
         claims-key (or (:claims-key opts) default-claims-key)]
     {:name ::require-auth
      :enter (fn [ctx]
               (if (get-in ctx [:request claims-key])
                 ctx
                 (sc/terminate ctx response)))})))
