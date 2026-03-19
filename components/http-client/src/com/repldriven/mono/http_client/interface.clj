(ns com.repldriven.mono.http-client.interface
  (:require
    [com.repldriven.mono.http-client.core :as client]))

(defn request [opts] (client/request opts))

(defn request-async [opts] (client/request-async opts))

(defn res->body [res] (client/res->body res))

(defn res->edn [res] (client/res->edn res))
