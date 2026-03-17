(ns lib.api)

(def ^:private api-key (atom nil))
(def ^:private api-keys-store "mono-api-keys")

(defn- admin-token
  []
  (.-VITE_MONO_ADMIN_API_KEY (.-env js/import.meta)))

(defn- parse-response
  [res]
  (-> (.json res)
      (.then
       (fn [body]
         #js {:http-status (.-status res) :body body}))))

(defn- load-keys
  "Loads the org-id->api-key map from localStorage."
  []
  (let [raw (.getItem js/localStorage api-keys-store)]
    (if raw
      (js->clj (js/JSON.parse raw))
      {})))

(defn- save-key
  "Persists a single org-id->api-key entry to localStorage."
  [org-id raw-key]
  (let [keys-map (assoc (load-keys) org-id raw-key)]
    (.setItem js/localStorage
              api-keys-store
              (js/JSON.stringify (clj->js keys-map)))))

(defn set-org
  "Switches the active API key to the one stored for org-id."
  [org-id]
  (reset! api-key (get (load-keys) org-id)))

(defn create-organization
  [org-name]
  (-> (js/fetch
       "/v1/organizations"
       #js {:method "POST"
            :headers
            #js {"Content-Type" "application/json"
                 "Authorization"
                 (str "Bearer " (admin-token))}
            :body
            (js/JSON.stringify #js {"name" org-name})})
      (.then parse-response)
      (.then
       (fn [res]
         (let [status (aget res "http-status")]
           (when (and (>= status 200) (< status 300))
             (let [body (.-body res)
                   org-id (aget body
                                "organization"
                                "organization-id")
                   raw-key (aget body "api-key" "raw-key")]
               (save-key org-id raw-key))))
         res))))

(defn list-organizations
  []
  (-> (js/fetch
       "/v1/organizations"
       #js {:headers
            #js {"Authorization"
                 (str "Bearer " (admin-token))}})
      (.then parse-response)))

(defn create-party
  [data]
  (let [{:strs [display-name given-name middle-names
                family-name date-of-birth nationality
                national-identifier]}
        (js->clj data)
        body (cond-> {"type" "PERSON"
                      "display-name" display-name
                      "given-name" given-name
                      "family-name" family-name
                      "date-of-birth" date-of-birth
                      "nationality" nationality}
                     middle-names
                     (assoc "middle-names" middle-names)
                     national-identifier
                     (assoc "national-identifier"
                            national-identifier))]
    (-> (js/fetch
         "/v1/parties"
         #js {:method "POST"
              :headers
              #js {"Content-Type" "application/json"
                   "Authorization"
                   (str "Bearer " @api-key)
                   "Idempotency-Key"
                   (str (random-uuid))}
              :body (js/JSON.stringify (clj->js body))})
        (.then parse-response))))

(defn list-parties
  [query-string]
  (let [url (if query-string
              (str "/v1/parties?" query-string)
              "/v1/parties")]
    (-> (js/fetch
         url
         #js {:headers
              #js {"Authorization"
                   (str "Bearer " @api-key)}})
        (.then parse-response))))

(defn open-cash-account
  [data]
  (let [{:strs [party-id name currency product-id]}
        (js->clj data)]
    (-> (js/fetch
         "/v1/cash-accounts"
         #js {:method "POST"
              :headers
              #js {"Content-Type" "application/json"
                   "Authorization"
                   (str "Bearer " @api-key)
                   "Idempotency-Key"
                   (str (random-uuid))}
              :body
              (js/JSON.stringify
               #js {"party-id" party-id
                    "name" name
                    "currency" currency
                    "product-id" product-id})})
        (.then parse-response))))

(defn close-cash-account
  [account-id]
  (-> (js/fetch
       (str "/v1/cash-accounts/" account-id "/close")
       #js {:method "POST"
            :headers
            #js {"Content-Type" "application/json"
                 "Authorization"
                 (str "Bearer " @api-key)
                 "Idempotency-Key"
                 (str (random-uuid))}})
      (.then parse-response)))

(defn list-cash-accounts
  [query-string]
  (let [url (if query-string
              (str "/v1/cash-accounts?" query-string)
              "/v1/cash-accounts")]
    (-> (js/fetch
         url
         #js {:headers
              #js {"Authorization"
                   (str "Bearer " @api-key)}})
        (.then parse-response))))

(defn create-cash-account-product
  [data]
  (let [{:strs [name account-type balance-sheet-side
                allowed-currencies balance-products]}
        (js->clj data)
        body (cond-> {"name" name
                      "account-type" account-type
                      "balance-sheet-side" balance-sheet-side}
                     (seq allowed-currencies)
                     (assoc "allowed-currencies"
                            allowed-currencies)
                     (seq balance-products)
                     (assoc "balance-products"
                            balance-products))]
    (-> (js/fetch
         "/v1/cash-account-products"
         #js {:method "POST"
              :headers
              #js {"Content-Type" "application/json"
                   "Authorization"
                   (str "Bearer " @api-key)}
              :body (js/JSON.stringify (clj->js body))})
        (.then parse-response))))

(defn list-cash-account-products
  []
  (-> (js/fetch
       "/v1/cash-account-products"
       #js {:headers
            #js {"Authorization"
                 (str "Bearer " @api-key)}})
      (.then parse-response)))

(defn publish-version
  [product-id version-id]
  (-> (js/fetch
       (str "/v1/cash-account-products/"
            product-id
            "/versions/"
            version-id
            "/publish")
       #js {:method "POST"
            :headers
            #js {"Content-Type" "application/json"
                 "Authorization"
                 (str "Bearer " @api-key)}})
      (.then parse-response)))

(defn create-cash-account-product-version
  [product-id data]
  (let [{:strs [name account-type balance-sheet-side
                allowed-currencies balance-products]}
        (js->clj data)
        body (cond-> {"name" name
                      "account-type" account-type
                      "balance-sheet-side" balance-sheet-side}
                     (seq allowed-currencies)
                     (assoc "allowed-currencies"
                            allowed-currencies)
                     (seq balance-products)
                     (assoc "balance-products"
                            balance-products))]
    (-> (js/fetch
         (str "/v1/cash-account-products/"
              product-id
              "/versions")
         #js {:method "POST"
              :headers
              #js {"Content-Type" "application/json"
                   "Authorization"
                   (str "Bearer " @api-key)}
              :body (js/JSON.stringify (clj->js body))})
        (.then parse-response))))

(defn list-balances
  [account-id]
  (-> (js/fetch
       (str "/v1/cash-accounts/" account-id "/balances")
       #js {:headers
            #js {"Authorization"
                 (str "Bearer " @api-key)}})
      (.then parse-response)))

(defn list-api-keys
  []
  (-> (js/fetch
       "/v1/api-keys"
       #js {:headers
            #js {"Authorization"
                 (str "Bearer " @api-key)}})
      (.then parse-response)))
