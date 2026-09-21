(ns com.repldriven.mono.server.interceptors
  (:require
    [com.repldriven.mono.auth.interface :as auth]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.identity-provider.interface :as idp]

    [sieppari.context :as sc]))

(def ^:private idempotency-key-re #"^[A-Za-z0-9_\-]{16,255}$")

(def require-idempotency-key
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

(def credential
  {:name ::credential
   :enter
   (fn [ctx]
     (let [header (get-in ctx [:request :headers "authorization"])
           credential (auth/header->token header auth/default-schemes)]
       (if credential (assoc-in ctx [:request :credential] credential) ctx)))})

(defn- with-claims
  [ctx claims]
  (if (and claims (not (error/anomaly? claims)))
    (assoc-in ctx [:request :auth-claims] claims)
    ctx))

(def authenticate-with-signer
  {:name ::authenticate-with-signer
   :enter (fn [ctx]
            (let [{:keys [request]} ctx
                  {:keys [signer credential]} request]
              (with-claims ctx
                           (when (and signer credential)
                             (auth/verify-token signer credential)))))})

(def authenticate-with-provider
  {:name ::authenticate-with-provider
   :enter (fn [ctx]
            (let [{:keys [request]} ctx
                  {:keys [identity-provider expected-audiences credential]}
                  request]
              (with-claims ctx
                           (when (and identity-provider credential)
                             (idp/verify-token identity-provider
                                               credential
                                               {:expected-audiences
                                                expected-audiences})))))})

(def ^:private default-unauthorized-response
  {:status 401
   :body {:title "UNAUTHORIZED"
          :type "server/unauthorized"
          :status 401
          :detail "Authentication required"}})

(def require-auth
  {:name ::require-auth
   :compile (fn [data _]
              (let [{:keys [unauthorized]} data
                    response (or unauthorized default-unauthorized-response)]
                {:enter (fn [ctx]
                          (if (get-in ctx [:request :auth-claims])
                            ctx
                            (sc/terminate ctx response)))}))})
