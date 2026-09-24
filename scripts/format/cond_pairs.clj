;; Lays out every `cond`, `cond->` and `cond->>` in the files named on the
;; command line as condition/action pairs, each on its own line with a blank
;; line between pairs, and rewrites the files in place. Only whitespace
;; between clauses changes, and indentation is left for zprint to restore, so
;; run zprint over the same files afterwards. A form with an odd number of
;; clauses is left as it is. Runs under babashka, which bundles rewrite-clj.
(ns cond-pairs
  (:require
    [rewrite-clj.node :as n]
    [rewrite-clj.parser :as p]
    [rewrite-clj.zip :as z]

    [clojure.string :as str]))

(def ^{:doc "Each form laid out, with the arguments it takes before its pairs."}
     leading-args
  '{cond 0 cond-> 1 cond->> 1})

(defn- significant?
  [node]
  (not (#{:whitespace :newline :comma :comment} (n/tag node))))

(defn- pairs-form
  [node]
  (when (= :list (n/tag node))
    (let [head (first (filter significant? (n/children node)))]
      (when (and head (= :token (n/tag head)) (symbol? (n/sexpr head)))
        (leading-args (n/sexpr head))))))

(defn- layout
  [nodes]
  (str/replace (apply str (map n/string nodes)) #"[ \t,]" ""))

(defn- gap
  "The nodes between two clauses, as `lines` newlines, or `nodes` as they
  are when they already are. A comment trailing the earlier clause stays on
  its line, and a comment on a line of its own stays above the later
  clause."
  [nodes lines]
  (let [same-line (take-while (fn [node] (not= :newline (n/tag node))) nodes)
        trailing (first (filter n/comment? same-line))
        own-line (remove #{trailing} (filter n/comment? nodes))
        newlines (cond-> lines
                         trailing
                         dec)
        laid (concat (when trailing [(n/spaces 1) trailing])
                     (when (pos? newlines) [(n/newlines newlines)])
                     own-line)]
    (if (= (layout nodes) (layout laid)) nodes laid)))

(defn- lay-out
  [node]
  (let [children (vec (n/children node))
        at (keep-indexed (fn [i child] (when (significant? child) i)) children)
        head-end (nth at (pairs-form node))
        clauses (drop (inc (pairs-form node)) at)]
    (if (or (< (count clauses) 2) (odd? (count clauses)))
      node
      (n/replace-children
       node
       (-> (reduce (fn [laid [k [prev clause]]]
                     (let [between (subvec children (inc prev) clause)]
                       (-> laid
                           (into (if (zero? k)
                                   between
                                   (gap between (if (odd? k) 1 2))))
                           (conj (children clause)))))
                   (subvec children 0 (inc head-end))
                   (map-indexed vector
                                (map vector (cons head-end clauses) clauses)))
           (into (subvec children (inc (last clauses)))))))))

(defn- rewrite
  [source]
  (-> (z/of-node* (p/parse-string-all source))
      (z/prewalk (fn [zloc] (some? (pairs-form (z/node zloc))))
                 (fn [zloc] (z/replace zloc (lay-out (z/node zloc)))))
      z/root-string))

(doseq [file *command-line-args*]
  (let [source (slurp file)
        rewritten (rewrite source)]
    (when (not= source rewritten)
      (spit file rewritten)
      (println "laid out" file))))
