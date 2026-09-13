(ns com.repldriven.mono.smtp.core
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.utility.interface :as util]

    [clojure.string :as str])
  (:import
    (java.io ByteArrayOutputStream)
    (java.util Properties)
    (jakarta.mail Address
                  AuthenticationFailedException
                  Message$RecipientType
                  SendFailedException
                  Session
                  Transport)
    (jakarta.mail.internet AddressException
                           InternetAddress
                           MimeBodyPart
                           MimeMessage
                           MimeMultipart)))

(def ^:private charset "UTF-8")

(defn session-properties
  [config]
  (let [{:keys [host port security username connection-timeout-ms timeout-ms
                properties]}
        config]
    (cond-> {"mail.smtp.host" (str host)
             "mail.smtp.port" (str port)
             "mail.smtp.connectiontimeout" (str connection-timeout-ms)
             "mail.smtp.timeout" (str timeout-ms)
             "mail.smtp.ssl.checkserveridentity" "true"}
            username
            (assoc "mail.smtp.auth" "true")

            (= :starttls security)
            (assoc "mail.smtp.starttls.enable" "true"
                   "mail.smtp.starttls.required" "true")

            (= :tls security)
            (assoc "mail.smtp.ssl.enable" "true")

            :always
            (into (map (fn [[k v]] [(name k) (str v)])) properties))))

(defn client
  [config]
  (error/try-nom :smtp/client
                 "Failed to build the SMTP session"
                 (let [{:keys [username password from]} config
                       props (doto (Properties.)
                               (.putAll (session-properties config)))]
                   {:session (Session/getInstance props)
                    :username username
                    :password password
                    :from from})))

(defn session? [x] (instance? Session x))

(defn- address-string
  [^Address address]
  (if (instance? InternetAddress address)
    (.getAddress ^InternetAddress address)
    (str address)))

(defn- address-strings [addresses] (mapv address-string addresses))

(defn- domain
  [^String address]
  (subs address (inc (str/last-index-of address "@"))))

(defn- invalid-address
  [address message]
  (error/reject :smtp/invalid-address
                (util/assoc-some {:message message} :address address)))

(defn- address
  [x]
  (let [addr (if (map? x) (:address x) x)
        parsed (when (string? addr)
                 (error/try-nom-ex :smtp/invalid-address
                                   AddressException
                                   "Invalid address"
                                   (doto (if (map? x)
                                           (InternetAddress. ^String addr
                                                             ^String (:name x)
                                                             ^String charset)
                                           (InternetAddress. ^String addr true))
                                     (.validate))))]
    (cond
     (nil? parsed)
     (invalid-address addr "Invalid address: not a string")

     (error/anomaly? parsed)
     (let [{:keys [exception]} (error/payload parsed)]
       (invalid-address addr (str "Invalid address: " (ex-message exception))))

     :else
     parsed)))

(defn- addresses
  [xs]
  (reduce (fn [acc x]
            (let [parsed (address x)]
              (if (error/anomaly? parsed) (reduced parsed) (conj acc parsed))))
          []
          (cond
           (sequential? xs)
           xs

           (nil? xs)
           []

           :else
           [xs])))

(defn- present? [s] (and (string? s) (not (str/blank? s))))

(defn- content
  [message]
  (let [missing (filterv (fn [k] (not (present? (get message k))))
                         [:subject :text])]
    (if (seq missing)
      (error/reject :smtp/invalid-message
                    {:missing missing
                     :message "A message needs a subject and a text body"})
      message)))

(defn- mime-message
  ^MimeMessage [^Session session ^String message-id]
  (proxy [MimeMessage] [session]
    (updateMessageID []
      (.setHeader ^MimeMessage this "Message-ID" message-id))))

(defn- address-array
  ^"[Ljakarta.mail.Address;" [addresses]
  (into-array Address addresses))

(defn- build
  [client message]
  (error/let-nom>
    [{:keys [subject text html headers]} (content message)
     to (if (seq (:to message))
          (addresses (:to message))
          (invalid-address nil "A message needs at least one :to address"))
     from-address (or (:from message) (:from client))
     ^InternetAddress from
     (if from-address
       (address from-address)
       (invalid-address nil "No :from on the message or the client"))
     cc (addresses (:cc message))
     bcc (addresses (:bcc message))
     reply-to (addresses (:reply-to message))
     session (or (:session client) (Session/getInstance (Properties.)))
     message-id (str "<" (util/uuidv7) "@" (domain (.getAddress from)) ">")
     msg (mime-message session message-id)]
    (.setFrom msg from)
    (.setRecipients msg Message$RecipientType/TO (address-array to))
    (when (seq cc)
      (.setRecipients msg Message$RecipientType/CC (address-array cc)))
    (when (seq bcc)
      (.setRecipients msg Message$RecipientType/BCC (address-array bcc)))
    (when (seq reply-to)
      (.setReplyTo msg (address-array reply-to)))
    (.setSubject msg ^String subject ^String charset)
    (if html
      (.setContent msg
                   (doto (MimeMultipart. "alternative")
                     (.addBodyPart (doto (MimeBodyPart.)
                                     (.setText ^String text ^String charset)))
                     (.addBodyPart (doto (MimeBodyPart.)
                                     (.setText ^String html
                                               ^String charset
                                               "html")))))
      (.setText msg ^String text ^String charset))
    (doseq [[k v] headers]
      (.setHeader msg (name k) (str v)))
    (.saveChanges msg)
    msg))

(defn render
  ([message] (render nil message))
  ([client message]
   (error/try-nom :smtp/render
                  "Failed to render the message"
                  (error/let-nom> [^MimeMessage msg (build client message)
                                   out (ByteArrayOutputStream.)]
                    (.writeTo msg out (into-array String ["Bcc"]))
                    (.toString out ^String charset)))))

(defn send-failure
  [anomaly]
  (let [{:keys [exception]} (error/payload anomaly)]
    (if (instance? SendFailedException exception)
      (let [^SendFailedException e exception]
        (update anomaly
                2
                assoc
                :invalid-addresses (address-strings (.getInvalidAddresses e))
                :valid-unsent (address-strings (.getValidUnsentAddresses e))
                :valid-sent (address-strings (.getValidSentAddresses e))))
      anomaly)))

(defn- outcome [result] (or (error/kind result) :accepted))

(defn- transmit
  [client ^MimeMessage msg]
  (let [{:keys [^Session session username password]} client]
    (send-failure
     (error/try-nom
      :smtp/send
      "Failed to send the message"
      (error/try-nom-ex
       :smtp/authenticate
       AuthenticationFailedException
       "The mail server rejected the credential"
       (let [^Transport transport (.getTransport session "smtp")]
         (try
           (if username
             (.connect transport ^String username ^String password)
             (.connect transport))
           (.sendMessage transport msg (.getAllRecipients msg))
           (finally (error/try-nom :smtp/close
                                   "Failed to close the transport"
                                   (.close transport))))))))))

(defn- traced-transmit
  [client ^MimeMessage msg message-id domains]
  (telemetry/with-span
   ["smtp/send"
    {"smtp.message_id" message-id
     "smtp.recipient_domains" (str/join "," domains)}]
   (let [result (transmit client msg)]
     (telemetry/set-attribute "smtp.outcome" (str (symbol (outcome result))))
     result)))

(defn send
  [client message]
  (let [built (error/try-nom :smtp/send
                             "Failed to build the message"
                             (build client message))]
    (if (error/anomaly? built)
      (do (log/info "SMTP send" {:outcome (outcome built)})
          built)
      (let [^MimeMessage msg built
            message-id (.getMessageID msg)
            recipients (address-strings (.getAllRecipients msg))
            domains (vec (sort (distinct (map domain recipients))))
            result (traced-transmit client msg message-id domains)]
        (log/info "SMTP send"
                  {:message-id message-id
                   :outcome (outcome result)
                   :recipient-domains domains})
        (if (error/anomaly? result)
          result
          {:message-id message-id :recipients recipients})))))
