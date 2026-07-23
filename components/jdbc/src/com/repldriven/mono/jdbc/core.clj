(ns com.repldriven.mono.jdbc.core
  "What the wrappers in `jdbc` and `sql` share: the anomaly
  boundary, the default options, and the result-key rewrite."
  (:require
    [com.repldriven.mono.jdbc.sql-state :as sql-state]

    [com.repldriven.mono.error.interface :refer [try-nom]]

    [next.jdbc :as jdbc]

    [clojure.set :as set]))

;; next.jdbc leaves naming to the caller, which for a workspace that keeps
;; kebab-case keyword keys everywhere means passing the same opts at every
;; call site. Default them once here instead: SQL sees snake_case, Clojure
;; sees unqualified kebab-case. Per-call opts still win.
(def default-opts jdbc/unqualified-snake-kebab-opts)

(defn opts+
  [opts]
  (merge default-opts opts))

;; A statement with no result set comes back keyed by
;; :next.jdbc/update-count. Re-namespace it to this brick, where the rest of
;; its vocabulary already lives — the anomaly kinds are :jdbc/… too. Kept
;; qualified rather than reduced to :update-count, because the qualifier is
;; what distinguishes driver metadata from a column that happens to be named
;; update_count.
(def ^:private renames {:next.jdbc/update-count :jdbc/update-count})

(defn plain
  [result]
  (cond (map? result)
        (set/rename-keys result renames)
        (vector? result)
        (mapv plain result)
        :else
        result))

;; Every wrapper in this brick goes through here rather than `try-nom`
;; directly, so that a failure arrives carrying its SQLSTATE. The codes are
;; the vocabulary databases actually speak; leaving them buried in the
;; exception would mean every caller digging them out again.
(defmacro sql-nom
  [category message & body]
  `(sql-state/enrich (try-nom ~category ~message ~@body)))
