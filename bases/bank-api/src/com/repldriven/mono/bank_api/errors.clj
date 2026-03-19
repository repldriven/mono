(ns com.repldriven.mono.bank-api.errors
  (:require
    [com.repldriven.mono.error.interface :as error]))

(defn error-response
  "Builds an RFC 9457-shaped ErrorResponse body.

  Two-arity form derives fields from an anomaly. 
  Three-arity form derives fields from a command response. 
  Four-arity form takes explicit title, type, and detail."
  ([status anomaly]
   (cond-> {:title (cond (error/unauthorized? anomaly)
                         "UNAUTHORIZED"
                         (error/rejection? anomaly)
                         "REJECTED"
                         :else
                         "FAILED")
            :type (str (error/kind anomaly))
            :status status}
           (:message (error/payload anomaly))
           (assoc :detail
                  (:message (error/payload anomaly)))))
  ([status command-status result]
   (cond-> {:title command-status :type (:reason result) :status status}
           (:message result)
           (assoc :detail (:message result))))
  ([status title type detail]
   (cond-> {:title title :type type :status status}
           detail
           (assoc :detail detail))))
