(ns com.repldriven.mono.server.openapi-test
  (:require
    [com.repldriven.mono.server.interface :as SUT]

    [reitit.coercion.malli :as malli]
    [reitit.ring :as ring]

    [clojure.test :refer [deftest is testing]]))

(def ^:private location
  {:description "The URI of the created thing." :schema {:type "string"}})

(def ^:private links
  {"GetThing" {:operationId "GetThing"
               :parameters {"thing-id" "$response.body#/thing-id"}}})

(def ^:private app
  (ring/ring-handler
   (ring/router [["/openapi.json"
                  {:get {:no-doc true :handler (SUT/standard-openapi-handler)}}]
                 ["/things"
                  {:post {:responses {201 {:body [:map [:thing-id :string]]
                                           :openapi {:headers {"Location"
                                                               location}
                                                     :links links}}
                                      400 {:body [:map [:title :string]]}}
                          :handler (constantly {:status 201})}}]]
                {:data {:coercion malli/coercion}})))

(deftest a-response-keeps-its-own-headers-and-links-test
  (let [responses (get-in (app {:request-method :get :uri "/openapi.json"})
                          [:body :paths "/things" :post :responses])
        created (get responses 201)]
    (testing "a response's headers reach the document"
      (is (= location (get-in created [:headers "Location"]))))
    (testing "and so do its links" (is (= links (:links created))))
    (testing "beside the content reitit builds"
      (is (contains? created :content)))
    (testing "a response that declares neither is left as reitit built it"
      (is (= #{:content} (set (keys (get responses 400))))))))
