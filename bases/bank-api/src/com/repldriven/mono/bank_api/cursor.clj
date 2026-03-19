(ns com.repldriven.mono.bank-api.cursor
  (:import
    (java.util Base64)))

(def ^:private prefix "v1:")

(defn encode
  "Encodes an id as an opaque cursor string."
  [id]
  (.encodeToString (Base64/getUrlEncoder) (.getBytes (str prefix id))))

(defn decode
  "Decodes a cursor string to an id. Returns nil on
  invalid or missing cursor."
  [cursor-str]
  (when cursor-str
    (try (let [decoded (String. (.decode (Base64/getUrlDecoder)
                                         ^String cursor-str))]
           (when (.startsWith decoded prefix) (subs decoded (count prefix))))
         (catch IllegalArgumentException _ nil))))
