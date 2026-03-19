(ns com.repldriven.mono.bank-api.api
  (:require
    [com.repldriven.mono.bank-api.balance.components :as balance.components]
    [com.repldriven.mono.bank-api.balance.examples :as balance.examples]
    [com.repldriven.mono.bank-api.balance.routes :as balance]
    [com.repldriven.mono.bank-api.cash-account-product.components :as
     cash-account-product.components]
    [com.repldriven.mono.bank-api.cash-account-product.examples :as
     cash-account-product.examples]
    [com.repldriven.mono.bank-api.cash-account-product.routes :as
     cash-account-product]
    [com.repldriven.mono.bank-api.cash-account.components :as
     cash-account.components]
    [com.repldriven.mono.bank-api.cash-account.examples :as
     cash-account.examples]
    [com.repldriven.mono.bank-api.cash-account.routes :as cash-account]
    [com.repldriven.mono.bank-api.api-key.components :as api-key.components]
    [com.repldriven.mono.bank-api.api-key.examples :as api-key.examples]
    [com.repldriven.mono.bank-api.api-key.routes :as api-key]
    [com.repldriven.mono.bank-api.auth :as auth]
    [com.repldriven.mono.bank-api.examples :as examples]
    [com.repldriven.mono.bank-api.organization.components :as
     organization.components]
    [com.repldriven.mono.bank-api.organization.examples :as
     organization.examples]
    [com.repldriven.mono.bank-api.organization.routes :as organization]
    [com.repldriven.mono.bank-api.party.components :as party.components]
    [com.repldriven.mono.bank-api.party.examples :as party.examples]
    [com.repldriven.mono.bank-api.party.routes :as party]
    [com.repldriven.mono.bank-api.schema :as schema]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [malli.core :as m]
    [malli.transform :as mt]
    [reitit.coercion.malli :as malli-coercion]
    [reitit.http :as http]
    [reitit.ring :as ring]))

(def ^:private api-transformer
  "Transformer for :decode/api and :encode/api properties
  on malli schemas. Composed with the base transformers
  to coerce API-friendly enum values to/from internal
  prefixed keywords."
  (mt/transformer {:name :api}))

(defn- ->provider
  "Creates a reitit TransformationProvider that composes
  base-transformer with api-transformer."
  [base-transformer]
  (reify
   malli-coercion/TransformationProvider
     (-transformer [_ {:keys [strip-extra-keys default-values]}]
       (mt/transformer (when strip-extra-keys
                         (mt/strip-extra-keys-transformer))
                       base-transformer
                       api-transformer
                       (when default-values (mt/default-value-transformer))))))

(def ^:private coercion
  (malli-coercion/create
   {:transformers {:body {:default (->provider (mt/json-transformer))}
                   :string {:default (->provider (mt/string-transformer))}
                   :response {:default (->provider nil)}}
    :options {:registry (merge (m/default-schemas)
                               {"Currency" schema/Currency
                                "ErrorResponse" schema/ErrorResponseSchema}
                               balance.components/registry
                               cash-account-product.components/registry
                               cash-account.components/registry
                               api-key.components/registry
                               organization.components/registry
                               party.components/registry)}}))

(defn- routes
  [ctx]
  [["/openapi.json"
    {:get {:no-doc true
           :openapi {:info {:title "Queenswood"
                            :description "Queenswood Banking API"
                            :version "1.0.0"}
                     :components
                     {:securitySchemes
                      {"adminAuth" {:type :http
                                    :scheme :bearer
                                    :description "Admin API key"}
                       "orgAuth" {:type :http
                                  :scheme :bearer
                                  :description "Organization API key"}}
                      :examples (merge examples/registry
                                       balance.examples/registry
                                       cash-account-product.examples/registry
                                       cash-account.examples/registry
                                       api-key.examples/registry
                                       organization.examples/registry
                                       party.examples/registry)}}
           :handler (server/standard-openapi-handler)}}]
   (into ["/v1"
          {:interceptors (concat telemetry/trace-span
                                 (:interceptors ctx)
                                 [auth/authenticate])
           :responses {400 (schema/ErrorResponse [#'examples/BadRequest])
                       401 (schema/ErrorResponse [#'examples/Unauthorized])
                       403 (schema/ErrorResponse [#'examples/Forbidden])
                       500 (schema/ErrorResponse [#'examples/InternalServerError
                                                  #'examples/BadResponse])}}]
         (concat balance/routes
                 cash-account-product/routes
                 cash-account/routes
                 api-key/routes
                 organization/routes
                 party/routes))])

(defn app
  [ctx]
  (http/ring-handler (http/router (routes ctx)
                                  (assoc-in server/standard-router-data
                                   [:data :coercion]
                                   coercion))
                     (ring/routes (server/standard-openapi-ui-handler)
                                  (ring/create-default-handler))
                     server/standard-executor))
