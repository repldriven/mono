#!/usr/bin/env bb
;; DEPRECATED. Prefer the deps-new template:
;;
;;   clojure -Tnew create \
;;     :template 'io.github.repldriven/mono%template%com.repldriven.mono/template#<tag>' \
;;     :name com.acme/my-thing
;;
;; This script forks and strips: the new repository ends up owning a full copy
;; of every shared brick, so an upstream fix can only reach it through a manual
;; merge. The template instead wires the new workspace to mono-lib as a pinned
;; git dependency, leaving only the example bricks to own and edit.
;;
;; Kept for now because existing forks were made with it.
;;
;; Usage: bb scripts/fork-domain.bb <domain-name>
;;
;; Strips the example domain and rewires configs for a new domain.

(require '[babashka.fs :as fs]
         '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.pprint :as pprint])

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(def root (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(defn die [& msgs]
  (binding [*out* *err*]
    (apply println msgs))
  (System/exit 1))

(defn example-key? [k]
  (str/includes? (str k) "example-"))

(defn example-path? [s]
  (str/includes? (str s) "example-"))

(defn info [& msgs]
  (apply println msgs))

;; ---------------------------------------------------------------------------
;; Validate args
;; ---------------------------------------------------------------------------

(def domain
  (let [args *command-line-args*]
    (when (or (empty? args) (str/blank? (first args)))
      (die "Usage: bb scripts/fork-domain.bb <domain-name>"))
    (let [d (str/lower-case (str/trim (first args)))]
      (when-not (re-matches #"[a-z][a-z0-9-]*" d)
        (die "Domain name must start with a letter and contain"
             "only lowercase letters, digits, and hyphens."))
      (when (= d "example")
        (die "Domain name cannot be 'example' — that's the"
             "one being removed."))
      d)))

;; ---------------------------------------------------------------------------
;; 1. Delete example directories
;; ---------------------------------------------------------------------------

(info "Deleting example directories...")

(doseq [dir (concat (fs/glob root "components/example-*")
                     (fs/glob root "bases/example-*")
                     (fs/glob root "projects/example-*"))]
  (when (fs/directory? dir)
    (info "  rm -rf" (str dir))
    (fs/delete-tree dir)))

;; ---------------------------------------------------------------------------
;; 2. Rewrite deps.edn
;; ---------------------------------------------------------------------------

(info "Rewriting deps.edn...")

(let [deps-path (str (fs/path root "deps.edn"))
      deps      (edn/read-string (slurp deps-path))
      example-alias (get-in deps [:aliases :+example])
      cleaned   (-> example-alias
                    (update :extra-deps
                            (fn [m]
                              (into (sorted-map)
                                    (remove (fn [[k _]]
                                              (example-key? k))
                                            m))))
                    (update :extra-paths
                            (fn [ps]
                              (vec (remove example-path? ps)))))
      new-key   (keyword (str "+" domain))
      deps'     (-> deps
                    (update :aliases dissoc :+example)
                    (assoc-in [:aliases new-key] cleaned))]
  (spit deps-path (with-out-str (pprint/pprint deps'))))

;; ---------------------------------------------------------------------------
;; 3. Rewrite workspace.edn
;; ---------------------------------------------------------------------------

(info "Rewriting workspace.edn...")

(let [ws-path (str (fs/path root "workspace.edn"))
      ws      (edn/read-string (slurp ws-path))
      ws'     (-> ws
                  (assoc :default-profile-name domain)
                  (update :projects
                          (fn [m]
                            (into {}
                                  (remove
                                    (fn [[k _]]
                                      (str/starts-with?
                                        (name k) "example-"))
                                    m)))))]
  (spit ws-path (with-out-str (pprint/pprint ws'))))

;; ---------------------------------------------------------------------------
;; 4. Rewrite .clj-kondo/config.edn
;; ---------------------------------------------------------------------------

(info "Rewriting .clj-kondo/config.edn...")

(let [kondo-path (str (fs/path root ".clj-kondo/config.edn"))
      cfg        (edn/read-string (slurp kondo-path))
      cfg'       (update-in cfg [:output :exclude-files]
                            (fn [fs]
                              (vec (remove
                                     #(str/includes?
                                        % "example-bookmark")
                                     fs))))]
  (spit kondo-path (with-out-str (pprint/pprint cfg'))))

;; ---------------------------------------------------------------------------
;; 5. Rewrite Justfile
;; ---------------------------------------------------------------------------

(info "Rewriting Justfile...")

(let [jf-path (str (fs/path root "Justfile"))
      content (slurp jf-path)
      content (str/replace content
                           #"DOMAIN_ALIASES := \":?\+example\""
                           (str "DOMAIN_ALIASES := \":+"
                                domain "\""))]
  (spit jf-path content))

;; ---------------------------------------------------------------------------
;; 6. Rewrite .github/workflows/test.yml
;; ---------------------------------------------------------------------------

(info "Rewriting .github/workflows/test.yml...")

(let [test-path (str (fs/path root
                               ".github/workflows/test.yml"))
      content   (slurp test-path)
      content   (str/replace content
                             "[:dev :+example]"
                             (str "[:dev :+" domain "]"))]
  (spit test-path content))

;; ---------------------------------------------------------------------------
;; 7. Rewrite readme.md
;; ---------------------------------------------------------------------------

(info "Rewriting readme.md...")

(let [readme-path (str (fs/path root "readme.md"))
      content     (slurp readme-path)
      ;; Remove Queenswood Bank callout
      content     (str/replace
                    content
                    #"(?s)> \*\*Looking for Queenswood Bank\?.*?\n\n"
                    "")
      content     (str/replace
                    content
                    #"(?s)## How to Use It\n.*?(?=\n## )"
                    (str "## How to Use It\n\n"
                         "Add your domain code:\n\n"
                         "1. Add domain components under"
                         " `components/" domain "-*/`\n"
                         "2. Add domain bases under"
                         " `bases/" domain "-*/`\n"
                         "3. Add domain projects under"
                         " `projects/" domain "-*/`\n"
                         "4. Register your new bricks in the"
                         " `:+" domain "` alias in `deps.edn`\n\n"
                         "See [Getting Started](#getting-started)"
                         " for prerequisites and how to\n"
                         "run tests.\n"))]
  (spit readme-path content))

;; ---------------------------------------------------------------------------
;; Done
;; ---------------------------------------------------------------------------

(info)
(info (str "Done! Example domain removed. Domain alias is :+"
           domain))
(info)
(info "Next steps:")
(info (str "  1. Add your domain components under components/"
           domain "-*/"))
(info (str "  2. Add your domain bases under bases/"
           domain "-*/"))
(info (str "  3. Add your domain projects under projects/"
           domain "-*/"))
(info (str "  4. Register deps in the :+" domain
           " alias in deps.edn"))
