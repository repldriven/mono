(ns com.repldriven.mono.server.openapi
  (:require
    [reitit.core :as r]
    [reitit.openapi :as openapi]))

(defn- response-openapi
  [router]
  (for [[path data] (r/routes router)
        [method operation] data
        :when (map? operation)
        [status response] (:responses operation)
        :let [openapi (:openapi response)]
        :when openapi]
    [path method status openapi]))

(defn- with-response-openapi
  [document router]
  (reduce (fn [document [path method status openapi]]
            (let [at [:paths path method :responses status]]
              (cond-> document
                      (get-in document at)
                      (update-in at merge openapi))))
          document
          (response-openapi router)))

(defn standard-handler
  []
  (let [handler (openapi/create-openapi-handler)
        complete (fn [request response]
                   (update response
                           :body
                           with-response-openapi
                           (::r/router request)))]
    (fn
      ([request] (complete request (handler request)))
      ([request respond raise]
       (handler request (comp respond (partial complete request)) raise)))))

(def ^:private ui-html
  "<!DOCTYPE html>
<html>
<head>
  <title>API Docs</title>
  <meta charset=\"utf-8\" />
  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
</head>
<body>
  <script
    id=\"api-reference\"
    data-url=\"/openapi.json\">
  </script>
  <script src=\"https://cdn.jsdelivr.net/npm/@scalar/api-reference\"></script>
</body>
</html>")

(defn standard-ui-handler
  []
  (fn [{:keys [request-method uri]}]
    (when (and (= :get request-method) (= "/" uri))
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body ui-html})))
