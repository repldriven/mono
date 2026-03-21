(ns com.repldriven.mono.bank-api.schema
  (:require
    [com.repldriven.mono.utility.interface :refer [vname]])
  (:import
    (java.time Instant)))

(defn components-registry
  [vars]
  (reduce (fn [m v] (assoc m (vname v) @v)) {} vars))

(defn examples-registry
  [examples]
  (reduce (fn [m v] (assoc m (vname v) @v)) {} examples))

(def ErrorResponseSchema
  [:map
   [:title string?]
   [:type string?]
   [:status int?]
   [:detail {:optional true} string?]])

(defn ErrorResponse
  [examples]
  {:content {"application/json"
             {:schema [:ref "ErrorResponse"]
              :examples (reduce (fn [m v]
                                  (let [v' (vname v)]
                                    (assoc m
                                           v'
                                           {"$ref" (str "#/components/examples/"
                                                        v')})))
                                {}
                                examples)}}})

(def Timestamp
  [:int
   {:encode/api (fn [ms] (when (pos? ms) (str (Instant/ofEpochMilli ms))))
    :json-schema {:type "string" :format "date-time"}}])


