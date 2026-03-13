(ns com.repldriven.mono.bank-api.api
  (:require
    [com.repldriven.mono.bank-api.account-products.components :as
     account-products.components]
    [com.repldriven.mono.bank-api.account-products.examples :as
     account-products.examples]
    [com.repldriven.mono.bank-api.account-products.routes :as
     account-products]
    [com.repldriven.mono.bank-api.accounts.components :as
     accounts.components]
    [com.repldriven.mono.bank-api.accounts.examples :as accounts.examples]
    [com.repldriven.mono.bank-api.accounts.routes :as accounts]
    [com.repldriven.mono.bank-api.api-keys.components :as
     api-keys.components]
    [com.repldriven.mono.bank-api.api-keys.examples :as api-keys.examples]
    [com.repldriven.mono.bank-api.api-keys.routes :as api-keys]
    [com.repldriven.mono.bank-api.auth :as auth]
    [com.repldriven.mono.bank-api.examples :as examples]
    [com.repldriven.mono.bank-api.organizations.components :as
     organizations.components]
    [com.repldriven.mono.bank-api.organizations.examples :as
     organizations.examples]
    [com.repldriven.mono.bank-api.organizations.routes :as organizations]
    [com.repldriven.mono.bank-api.parties.components :as
     parties.components]
    [com.repldriven.mono.bank-api.parties.examples :as parties.examples]
    [com.repldriven.mono.bank-api.parties.routes :as parties]
    [com.repldriven.mono.bank-api.schema :as schema]

    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.telemetry.interface :as telemetry]

    [malli.core :as m]
    [reitit.coercion.malli :as malli-coercion]
    [reitit.http :as http]
    [reitit.ring :as ring]))

(def ^:private coercion
  (malli-coercion/create
   {:options {:registry (merge (m/default-schemas)
                               {"Currency" schema/Currency
                                "ErrorResponse" schema/ErrorResponseSchema}
                               account-products.components/registry
                               accounts.components/registry
                               api-keys.components/registry
                               organizations.components/registry
                               parties.components/registry)}}))

(defn- routes
  [ctx]
  [["/openapi.json"
    {:get {:no-doc true
           :openapi
           {:info {:title "Queenswood"
                   :description "Queenswood Banking API"
                   :version "1.0.0"}
            :components
            {:securitySchemes
             {"adminAuth"
              {:type :http :scheme :bearer :description "Admin API key"}
              "orgAuth"
              {:type :http :scheme :bearer :description "Organization API key"}}
             :examples (merge examples/registry
                              account-products.examples/registry
                              accounts.examples/registry
                              api-keys.examples/registry
                              organizations.examples/registry
                              parties.examples/registry)}}
           :handler (server/standard-openapi-handler)}}]
   (into ["/v1"
          {:interceptors (concat telemetry/trace-span
                                 (:interceptors ctx)
                                 [auth/authenticate])
           :responses
           {400 (schema/ErrorResponse [#'examples/BadRequest])
            401 (schema/ErrorResponse [#'examples/Unauthorized])
            403 (schema/ErrorResponse [#'examples/Forbidden])
            500 (schema/ErrorResponse [#'examples/InternalServerError
                                       #'examples/BadResponse])}}]
         (concat account-products/routes
                 accounts/routes
                 api-keys/routes
                 organizations/routes
                 parties/routes))])

(defn app
  [ctx]
  (http/ring-handler (http/router (routes ctx)
                                  (assoc-in server/standard-router-data
                                   [:data :coercion]
                                   coercion))
                     (ring/routes (server/standard-openapi-ui-handler)
                                  (ring/create-default-handler))
                     server/standard-executor))
