(ns com.repldriven.mono.http-client.core
  (:require
    [com.repldriven.mono.error.interface :as err]
    [org.httpkit.client :as client]
    [clojure.data.json :as json]
    [clojure.string :as str]))

(defn request
  [opts]
  (err/try-nom :http-client/request
               "HTTP request threw an exception"
               (let [res @(client/request opts)]
                 (if-let [error (:error res)]
                   (err/fail :http-client/request
                             "HTTP request failed"
                             {:opts opts :error error :res res})
                   res))))

(defn request-async
  [opts]
  (client/request opts))

(defn- body->string
  [body]
  (cond (nil? body)
        nil
        (string? body)
        body
        (instance? java.io.InputStream body)
        (slurp body)
        (bytes? body)
        (String. ^bytes body "UTF-8")
        :else
        (str body)))

(defn res->body
  ([res] (res->body res nil))
  ([res opts]
   (cond (err/anomaly? res)
         res
         (nil? res)
         nil
         :else
         (err/try-nom :http-client/body-parse
                      "Failed to parse response body"
                      (when-let [{:keys [body headers]} res]
                        (when-let [body-str (body->string body)]
                          (let [content-type (:content-type headers)]
                            (if (and content-type
                                     (str/includes? content-type "json"))
                              (json/read-str body-str opts)
                              body-str))))))))

(defn res->edn
  [res]
  (res->body res {:key-fn keyword}))
