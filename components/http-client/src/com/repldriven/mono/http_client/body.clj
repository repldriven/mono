(ns com.repldriven.mono.http-client.body
  "Reading a response body.

  Separate from `core`: nothing here calls http-kit. It takes what a
  request returned — a response map, an anomaly, or nil — and gets a
  body out of it."
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.json.interface :as json]

    [clojure.string :as str]))

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
   (cond (error/anomaly? res)
         res
         (nil? res)
         nil
         :else
         (error/try-nom
          :http-client/body-parse
          "Failed to parse response body"
          (when-let [{:keys [body headers]} res]
            (when-let [body-str (body->string body)]
              (let [content-type (:content-type headers)]
                (if (and content-type
                         (str/includes? content-type "json"))
                  (apply json/read-str body-str (apply concat opts))
                  body-str))))))))

(defn res->edn
  [res]
  (res->body res {:key-fn keyword}))
