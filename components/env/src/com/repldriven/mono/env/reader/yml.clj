(ns com.repldriven.mono.env.reader.yml
  (:require
    [com.repldriven.mono.env.reader.edn :as reader.edn]

    [com.repldriven.mono.utility.interface :as util]

    [clj-yaml.core :as yaml]

    [clojure.java.io :as io]
    [clojure.string :as str]))

(defmulti yml-reader (fn [m] (keyword (get m :tag))))

;; Default - return the value as-is
(defmethod yml-reader :default [m] (:value m))

;; Basic common tag readers
(defmethod yml-reader :!profile
  [{:keys [value]}]
  (symbol (str "#profile " (util/yaml-collections->edn-collections value))))

(defmethod yml-reader :!port [{:keys [value]}] (symbol (str "#port " value)))

(defmethod yml-reader :!include
  [{:keys [value]}]
  (let [key-fn (fn [{:keys [key]}]
                 (if (and (str/starts-with? key "\"") (str/ends-with? key "\""))
                   (subs key 1 (dec (count key)))
                   (keyword key)))]
    (-> value
        io/resource
        io/reader
        (yaml/parse-stream {:key-fn key-fn :unknown-tag-fn yml-reader})
        util/yaml-collections->edn-collections)))

(defmethod yml-reader :!env [{:keys [value]}] (System/getenv (name value)))

;; `!long 8080`, or `!long [!or [!env PORT, 8080]]` to wrap another tag —
;; a one-item sequence, because YAML rejects a tag on a tagged scalar but
;; allows one on a sequence item.
(defmethod yml-reader :!long
  [{:keys [value]}]
  (symbol (str "#long " (if (sequential? value) (first value) value))))

;; First value that resolves. Aero needs any coercion outside this, not in
;; it: `#or [#long #env "X" 8080]` throws on the absent case before the
;; fallback is reached, where `#long #or [...]` does not.
(defmethod yml-reader :!or
  [{:keys [value]}]
  (symbol (str "#or " (util/yaml-collections->edn-collections value))))

;; Concatenates its sequence into one string, so a value can be built from
;; parts: `!join ["meta-", !uuid ""]`.
(defmethod yml-reader :!join
  [{:keys [value]}]
  (symbol (str "#join " (util/yaml-collections->edn-collections value))))

;; `!random-uuid` generates one. It takes no value, but EDN has no
;; zero-argument tagged literal, so a nil is supplied for it.
(defmethod yml-reader :!random-uuid [_] (symbol "#random-uuid nil"))

;; `!uuid "0192-..."` parses a literal, as `#uuid` does in EDN.
(defmethod yml-reader :!uuid
  [{:keys [value]}]
  (symbol (str "#uuid " (pr-str value))))

;; `!keyword none`, or `!keyword [!or [!env SECURITY, starttls]]` to wrap
;; another tag, as `!long` does: the sequence form defers to aero's
;; `#keyword`, so the keyword is made from what the inner tags resolve to.
(defmethod yml-reader :!keyword
  [{:keys [value]}]
  (if (sequential? value)
    (symbol (str "#keyword " (first value)))
    (keyword value)))

(defmethod yml-reader :!keywords
  [{:keys [value]}]
  (util/val-strs->keywords value))

(defmethod yml-reader :!str [{:keys [value]}] (str "\"" (name value) "\""))

(defmethod yml-reader :!strs [{:keys [value]}] (util/keys->strs value))

;; `!concat` flattens a sequence of sequences into one sequence — handy
;; for splicing several `!include`d lists (e.g. per-domain capability
;; files) into a single parent list, since plain YAML can't merge
;; sequences across `!include` boundaries.
(defmethod yml-reader :!concat [{:keys [value]}] (vec (apply concat value)))

(defn- key-fn
  [{:keys [key]}]
  (if (and (str/starts-with? key "\"") (str/ends-with? key "\""))
    (subs key 1 (dec (count key)))
    (keyword key)))

(defn config
  [source profile]
  (-> (util/resolve-source source)
      io/reader
      (yaml/parse-stream {:key-fn key-fn :unknown-tag-fn yml-reader})
      util/yaml-collections->edn-collections
      str
      util/string->stream
      (reader.edn/read-config profile)))
