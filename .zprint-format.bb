#!/usr/bin/env bb

(require '[babashka.deps :as deps])
(deps/add-deps '{:deps {zprint/zprint {:mvn/version "1.2.9"}}})

(require '[zprint.core :as zp])

(zp/set-options! {:search-config? true})

(let [file (first *command-line-args*)]
  (when file
    (let [content (slurp file)
          formatted (zp/zprint-file-str content file)]
      (spit file formatted))))
