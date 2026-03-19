(ns com.repldriven.mono.server.openapi
  (:require
    [reitit.openapi :as openapi]))

(defn standard-handler [] (openapi/create-openapi-handler))

(defn standard-ui-handler
  []
  (fn [_]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body
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
</html>"}))
