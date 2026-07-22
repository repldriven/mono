(ns com.repldriven.mono.template.new
  "deps-new template functions for generating a Polylith workspace built on the
  mono component library.

  Two hooks, with a deliberate split of responsibility.

  `template-fn` runs before deps-new computes its substitution map, and whatever
  it returns is merged into that map. So it resolves the pinned mono release,
  reads the starter manifest, and computes every *data* fragment the generated
  files need, including the registration blocks that get substituted into
  deps.edn. Doing that here rather than by editing generated files afterwards
  means no EDN zipper surgery is needed anywhere.

  `post-process-fn` runs after deps-new has copied the wiring files, and owns
  the one thing deps-new cannot express: staging the starter bricks out of the
  mono checkout, rewriting their namespaces and file paths.

  Why staging is not a `:transform`. deps-new copies through
  `tools.build.api/copy-dir` with a `:replace` map whose keys are always
  literally `{{something}}`, so an arbitrary `com.repldriven.mono.X` to
  `<top-ns>.X` replacement cannot be expressed; and a transform's `:files`
  rename map is flat, so moving `src/com/repldriven/mono/example_bookmark/` to
  `src/com/acme/bookmarks/example_bookmark/` cannot be expressed either."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [clojure.tools.gitlibs :as gitlibs])
  (:import
    (java.io File)
    (java.util.regex Pattern)))

(def ^:private mono-lib 'io.github.repldriven/mono)
(def ^:private mono-ns "com.repldriven.mono")
(def ^:private mono-path "com/repldriven/mono")

(defn- die
  [message data]
  (throw (ex-info message data)))

(defn- ->munged
  "Namespace segment to its file and Java package form."
  [s]
  (str/replace (str s) "-" "_"))

(defn- ->path
  [s]
  (-> (str s)
      (str/replace "." "/")
      (->munged)))

;; ---------------------------------------------------------------------------
;; template-fn
;; ---------------------------------------------------------------------------

(defn- starter-manifest
  "Read starter.edn from the template directory. deps-new always sets
  :template-dir to the directory holding template.edn, so this works whether the
  template was resolved from the classpath or from :src-dirs."
  [template-dir]
  (let [f (io/file template-dir "starter.edn")]
    (when-not (.exists f)
      (die "starter.edn is missing from the template"
           {:template-dir template-dir}))
    (edn/read-string (slurp f))))

(defn- resolve-mono
  "Resolve the pinned mono release to a checkout directory on disk.

  Normally the tag is resolved through tools.gitlibs, which populates and reuses
  the same ~/.gitlibs checkout a real dependency resolution would, so the
  starter bricks always match the mono-lib version being pinned.

  Passing :mono/dir short circuits that to a working copy. That is for
  developing the template itself, and is not how a consumer generates."
  [{:mono/keys [url tag sha dir]}]
  (if dir
    (let [f (io/file dir)]
      (when-not (.isDirectory (io/file f "components"))
        (die (str "'" dir "' is not a mono checkout: no components directory")
             {:mono/dir dir}))
      {:url url
       :tag (or tag "LOCAL")
       :sha (or sha "LOCAL")
       :dir (.getCanonicalPath f)
       ;; Only a caller-supplied :mono/dir means development mode. The
       ;; normal path also ends up with a directory, but a checkout under
       ;; ~/.gitlibs, which must never be written into a generated
       ;; deps.edn: it is specific to this machine and disappears when the
       ;; cache is cleared.
       :local? true})
    (do
      (when-not (and url tag)
        (die "template.edn must set :mono/url and :mono/tag" {}))
      (let [resolved (gitlibs/resolve url tag)]
        (when-not resolved
          (die (str "Cannot resolve mono tag '" tag "' from " url)
               {:url url :tag tag}))
        ;; A published tag must never move. If one has, fail loudly rather
        ;; than generating a workspace pinned to a sha nobody can
        ;; reproduce.
        (when (and sha (not= sha resolved))
          (die (str "Pinned :mono/sha does not match the sha that tag '"
                    tag
                    "' resolves to. A published tag has been moved.")
               {:tag tag :pinned sha :resolved resolved}))
        {:url url
         :tag tag
         :sha resolved
         :dir (gitlibs/procure url mono-lib resolved)
         :local? false}))))

(defn- workspace-top-ns
  "The Polylith :top-namespace for the generated workspace.

  Defaults to '<top>.<main>', so ':name com.acme/bookmarks' gives
  'com.acme.bookmarks', mirroring mono's own com.repldriven + mono.

  Note that deps-new derives :top by stripping known git service prefixes, so
  ':name io.github.acme/bookmarks' gives 'acme.bookmarks', not
  'io.github.acme.bookmarks'. Pass :top-ns to say exactly what you want."
  [{:keys [top main top-ns]}]
  (let [t (str/replace (str (or top-ns (str top "." main))) "_" "-")]
    (when-not (re-matches #"[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)+" t)
      (die (str "Top namespace '"
                t
                "' must be two or more lower case segments. "
                "Pass :top-ns explicitly, for example "
                ":top-ns '\"com.acme.bookmarks\"'.")
           {:top-ns t}))
    t))

(defn- brick-lib
  "Dep key for a staged brick in the generated workspace. Polylith identifies a
  brick by its :local/root and discards the key, so qualifying the key with the
  workspace top namespace costs nothing and avoids collisions with keys that
  arrive from mono-lib."
  [top-ns {:keys [kind name]}]
  (symbol (str top-ns "." kind) name))

(defn- lines
  "Join pre-rendered lines at a fixed indent, so a substituted block sits
  correctly inside the hand-written deps.edn it lands in. The first line is
  trimmed because the placeholder is already positioned in that file."
  [n coll]
  (let [pad (apply str (repeat n \space))]
    (->> coll
         (map #(str pad %))
         (str/join "\n")
         (str/triml))))

(defn- local-root
  "Render a :local/root coordinate by hand. pr-str would emit the namespaced map
  form #:local{:root \"...\"}, which is valid EDN and resolves correctly, but is
  not how anyone writes a deps.edn."
  [path]
  (str "{:local/root \"" path "\"}"))

(defn- coord
  "Render the coordinate for one mono artifact, as a string to be substituted
  into a generated deps.edn. `n` is the column continuation lines align to.

  In the normal case this is a pinned git coordinate. Only when the caller
  supplied :mono/dir is it a :local/root into that working copy, so a generated
  workspace can be exercised before the release it would otherwise depend on
  exists. That is what makes the pre-release smoke test possible: the template
  pins a release of the very repository it lives in.

  Branching on :mono/local? rather than on the presence of a directory is
  essential. The published path also has a directory, but it is a ~/.gitlibs
  checkout; writing that into a generated deps.edn would produce a workspace
  that only resolves on the machine that generated it."
  [{:mono/keys [url tag sha dir local?]} root n]
  (if local?
    (local-root (str dir "/" root))
    (lines n
           [(str "{:git/url \"" url "\"")
            (str ":git/tag \"" tag "\"")
            (str ":git/sha \"" sha "\"")
            (str ":deps/root \"" root "\"}")])))

(defn- registration-data
  "Compute the substitution fragments that register the starter bricks.

  Mono registers a brick at three sites in lockstep: the profile alias's
  :extra-deps and :extra-paths in the top level deps.edn, and :deps in the
  project deps.edn. The generated workspace reproduces that shape, and computing
  it here means the generated files can be plain templates with placeholders."
  [{:keys [mono-dir top-ns bricks]}]
  (let [profile (filter :profile? bricks)
        exists? (fn [{:keys [kind name]} sub]
                  (.isDirectory (io/file mono-dir kind name sub)))]
    {:starter/profile-deps
     (lines 4
            (for [b profile]
              (str (brick-lib top-ns b)
                   " "
                   (local-root (str (:kind b) "/" (:name b))))))
     ;; aligned one per line under the opening bracket, which sits at
     ;; column 17 of the :extra-paths line in the generated deps.edn
     :starter/profile-paths
     (str "["
          (lines 17
                 (for [b profile
                       sub ["test" "test-resources"]
                       :when (exists? b sub)]
                   (str "\"" (:kind b) "/" (:name b) "/" sub "\"")))
          "]")
     :starter/project-deps
     (lines 8
            (for [b bricks]
              (str (brick-lib top-ns b)
                   " "
                   (local-root (str "../../" (:kind b) "/" (:name b))))))
     :starter/kondo-excludes
     (lines 3
            (for [b bricks
                  :when (:gen? b)]
              (pr-str (str (:kind b)
                           "/"
                           (:name b)
                           "/gen/"
                           (->path top-ns)
                           "/.*\\.cljc"))))}))

(defn template-fn
  "deps-new :template-fn. Receives the raw template.edn and the options, and
  returns the template EDN augmented with substitution data.

  Everything returned here lands in the substitution map, so generated files can
  refer to {{mono/url}}, {{mono/tag}}, {{mono/sha}}, {{top-ns}}, {{top-ns/file}}
  and the {{starter/*}} registration blocks.

  :top-ns is left unqualified so deps-new synthesises the /ns and /file variants
  from it. The :mono/* and :starter/* keys are qualified, so they substitute as
  a single form each, which is all they need."
  [edn {:keys [template-dir] :as opts}]
  (let [;; deps-new merges as (merge desc edn opts), so CLI options win
        merged (merge edn opts)
        manifest (starter-manifest template-dir)
        {:keys [url tag sha dir local?]} (resolve-mono merged)
        top-ns (workspace-top-ns merged)
        bricks (:bricks manifest)]
    (doseq [{:keys [kind name]} bricks]
      (when-not (.isDirectory (io/file dir kind name))
        (die (str "Starter brick " kind "/" name " is not present in mono " tag)
             {:mono/dir dir :kind kind :name name})))
    (println "Using mono" tag "at" sha)
    (let [mono {:mono/url url
                :mono/tag tag
                :mono/sha sha
                :mono/dir dir
                :mono/local? local?}]
      (merge
       edn
       mono
       {:mono/manifest manifest
        :top-ns top-ns
        ;; Coordinates are substituted rather than written literally into
        ;; the committed files, so that development mode can swap pinned
        ;; git deps for local roots. Each indent matches where the
        ;; placeholder sits in the file it lands in.
        :mono/lib-coord (coord mono "projects/mono-lib" 5)
        :mono/test-lib-coord (coord mono "projects/mono-test-lib" 5)
        :mono/test-runner-coord (coord mono "bases/external-test-runner" 5)
        :mono/project-lib-coord (coord mono "projects/mono-lib" 9)
        :mono/project-test-lib-coord (coord mono "projects/mono-test-lib" 5)
        :mono/project-test-runner-coord
        (coord mono "bases/external-test-runner" 5)
        :mono/build-coord (coord mono "bases/build" 24)}
       (registration-data {:mono-dir dir :top-ns top-ns :bricks bricks})))))

;; ---------------------------------------------------------------------------
;; post-process-fn: rewriting
;; ---------------------------------------------------------------------------

(defn- rewrite-rules
  "Ordered [regex replacement] pairs mapping the starter bricks' mono namespaces
  onto the consumer's top namespace.

  Up to four shapes per segment, because the same name appears as a hyphenated
  Clojure namespace, as a munged Java or protobuf package, and as both forms of
  file path. Segments are applied longest first and each pattern ends in a
  negative lookahead, so one segment cannot match a prefix of a longer sibling.

  Only the listed segments are rewritten. That is the whole point of the design:
  a reference to com.repldriven.mono.error is left alone, because error arrives
  from the frozen mono-lib dependency."
  [top-ns segments]
  (let [top-munged (->munged top-ns)
        top-file (->path top-ns)
        boundary "(?![-\\w])"
        rule (fn [from to]
               [(re-pattern (str (Pattern/quote from) boundary)) to])]
    (into []
          (mapcat
           (fn [seg]
             (let [m (->munged seg)]
               (cond-> [(rule (str mono-ns "." seg) (str top-ns "." seg))
                        (rule (str mono-path "/" m) (str top-file "/" m))]
                       ;; a segment with no hyphen has identical plain and
                       ;; munged forms, so adding both would duplicate work
                       (not= m seg)
                       (into [(rule (str mono-ns "." m) (str top-munged "." m))
                              (rule (str mono-path "/" seg)
                                    (str top-file "/" seg))]))))
           (sort-by (comp - count) segments)))))

(defn- rewrite
  [s rules]
  (reduce (fn [acc [re replacement]]
            (str/replace acc re (str/re-quote-replacement replacement)))
          s
          rules))

;; ---------------------------------------------------------------------------
;; post-process-fn: staging
;; ---------------------------------------------------------------------------

(defn- excluded?
  [rel patterns]
  (boolean (some #(re-find (re-pattern %) rel) patterns)))

(defn- binary?
  [^File f exts]
  (contains? (set exts)
             (some-> (re-find #"\.([^.]+)$" (.getName f))
                     second
                     str/lower-case)))

(defn- stage-brick!
  "Copy one brick out of the mono checkout into the generated workspace,
  rewriting both the file path and, for text files, the file contents."
  [{:keys [mono-dir target-dir kind name rules exclude binary-exts]}]
  (let [src-root (io/file mono-dir kind name)
        dst-root (io/file target-dir kind name)
        prefix (inc (count (.getPath src-root)))
        copied (atom 0)]
    (doseq [^File f (file-seq src-root)
            :when (.isFile f)]
      (let [rel (subs (.getPath f) prefix)]
        (when-not (excluded? rel exclude)
          (let [dst (io/file dst-root (rewrite rel rules))]
            (io/make-parents dst)
            (if (binary? f binary-exts)
              (io/copy f dst)
              (spit dst (rewrite (slurp f) rules)))
            (swap! copied inc)))))
    (println (str "  staged " kind "/" name " (" @copied " files)"))))

;; ---------------------------------------------------------------------------
;; post-process-fn: relinking a staged brick's own deps.edn
;; ---------------------------------------------------------------------------

(defn- git-coord
  "Coordinate for a mono brick a staged brick depends on. Mirrors `coord`, but
  returns data rather than a rendered string, because this one is written back
  through the EDN printer."
  [{:mono/keys [url tag sha dir local?]} root]
  (if local?
    {:local/root (str dir "/" root)}
    (cond-> {:git/url url :git/tag tag :git/sha sha}
            root
            (assoc :deps/root root))))

(defn- relink
  [deps table opts]
  (reduce-kv (fn [m k coord]
               (if-let [entry (get table (:local/root coord))]
                 (assoc m (:lib entry) (git-coord opts (:deps/root entry)))
                 (assoc m k coord)))
             {}
             deps))

(defn- relink-alias
  [a table opts]
  (cond-> a
          (:deps a)
          (update :deps relink table opts)
          (:extra-deps a)
          (update :extra-deps relink table opts)))

(defn- relink-brick-deps!
  "Replace a staged brick's :local/root references to mono bricks that are not
  shipped in mono-lib with a pinned git dep on the mono repo."
  [file table opts]
  (let [d (edn/read-string (slurp file))
        relinked (cond-> d
                         (:deps d)
                         (update :deps relink table opts)
                         (:aliases d)
                         (update :aliases
                                 update-vals
                                 #(relink-alias % table opts)))]
    (when (not= d relinked)
      (spit file (with-out-str (pprint/pprint relinked)))
      (println (str "  relinked " (.getPath ^File file))))))

;; ---------------------------------------------------------------------------

(defn post-process-fn
  "deps-new :post-process-fn. Everything it needs is in the options, because
  template-fn put it there."
  [_edn {:keys [target-dir top-ns] :as opts}]
  (let [{:mono/keys [dir tag manifest]} opts
        {:keys [bricks rewrite-segments exclude binary-exts
                local-root->git]}
        manifest
        rules (rewrite-rules top-ns rewrite-segments)]
    (println "Staging starter bricks from mono" tag)
    (doseq [b bricks]
      (stage-brick! (assoc b
                           :mono-dir dir
                           :target-dir target-dir
                           :rules rules
                           :exclude exclude
                           :binary-exts binary-exts))
      (let [f (io/file target-dir (:kind b) (:name b) "deps.edn")]
        (when (.exists f)
          (relink-brick-deps! f local-root->git opts))))
    (println)
    (println "Workspace created. Next:")
    (println "  cd" target-dir)
    (println "  clojure -X:deps prep :aliases '[:dev :+example]'")
    (println "  clojure -M:poly check")))
