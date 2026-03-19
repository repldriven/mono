(ns com.repldriven.mono.bank-party.commands
  (:require
    [com.repldriven.mono.bank-party.domain :as domain]
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.bank-schema.interface :as schema])
  (:import
    (com.apple.foundationdb.record RecordIndexUniquenessViolation)))

(defn- save-party
  "Saves party to store, writes changelog entry with
  serialized changelog proto, returns protobuf record or
  anomaly."
  [store party changelog]
  (error/let-nom> [_ (fdb/save-record store (schema/Party->java party))
                   _
                   (fdb/write-changelog store
                                        "parties"
                                        (:party-id party)
                                        (schema/PartyChangelog->pb changelog))]
    (schema/Party->pb party)))

(defn- save-person-identification
  "Saves person-identification to store."
  [store person-id]
  (fdb/save-record store (schema/PersonIdentification->java person-id)))

(defn- save-party-national-identifier
  "Saves party-national-identifier to store."
  [store party-ni]
  (fdb/save-record store (schema/PartyNationalIdentifier->java party-ni)))

(defn- uniqueness-violation?
  "Returns true if anomaly was caused by a
  RecordIndexUniquenessViolation."
  [anomaly]
  (when (error/anomaly? anomaly)
    (loop [ex (:exception (error/payload anomaly))]
      (cond (nil? ex)
            false
            (instance? RecordIndexUniquenessViolation ex)
            true
            :else
            (recur (.getCause ex))))))

(defn- create-person
  "Creates a person party with person-identification and
  optional national-identifier in a single transaction."
  [record-db record-store data]
  (fdb/transact-multi
   record-db
   record-store
   (fn [open-store]
     (let [party (domain/new-party data)
           party-id (:party-id party)
           person-id (domain/new-person-identification data
                                                       party-id)
           party-store (open-store "parties")
           pid-store (open-store "person-identifications")
           ni (:national-identifier data)]
       (error/let-nom>
         [_ (save-person-identification pid-store person-id)
          _
          (if ni
            (save-party-national-identifier
             (open-store "party-national-identifiers")
             (domain/new-party-national-identifier
              ni
              (:organization-id party)
              party-id))
            nil)
          result
          (save-party party-store
                      party
                      {:organization-id (:organization-id party)
                       :party-id party-id
                       :status-after (:status party)})]
         result)))))

(defn- create-internal
  "Creates an internal party — no person-identification or
  national-identifier."
  [record-db record-store data]
  (fdb/transact record-db
                record-store
                "parties"
                (fn [store]
                  (let [party (domain/new-party data)]
                    (save-party store
                                party
                                {:organization-id (:organization-id party)
                                 :party-id (:party-id party)
                                 :status-after (:status party)})))))

(defn create
  "Creates a party. Internal parties skip person-identification
  and national-identifier. Returns protobuf party record or
  anomaly."
  [config data]
  (let [{:keys [record-db record-store]} config
        result (if (= :party-type-internal (:type data))
                 (create-internal record-db record-store data)
                 (create-person record-db record-store data))]
    (if (uniqueness-violation? result)
      (error/reject :bank-party/duplicate-national-identifier
                    "National identifier already exists")
      result)))

(defn- ->response
  "Converts a protobuf record to an ACCEPTED response.
  Returns anomalies unchanged for the processor to handle."
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      {:status "ACCEPTED"
       :payload (avro/serialize (schemas "party") (schema/pb->Party result))})))

(defn create-party
  "Creates a new party."
  [config data]
  (->response config (create config data)))
