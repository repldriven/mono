(ns com.repldriven.mono.utility.interface
  (:refer-clojure :exclude [vars])
  (:require
    [com.repldriven.mono.utility.collections :as util.collections]
    [com.repldriven.mono.utility.string :as util.string]
    [com.repldriven.mono.utility.uuid :as util.uuid]
    [com.repldriven.mono.utility.vars :as vars]))

;; Collection utilities
(def deep-merge util.collections/deep-merge)
(def record->map util.collections/record->map)

;; String utilities
(def str->bytes util.string/str->bytes)
(def string->stream util.string/string->stream)
(def resolve-source util.string/resolve-source)
(def prop-seq->kw-map util.string/prop-seq->kw-map)

(def yaml-collections->edn-collections
  util.collections/yaml-collections->edn-collections)
(def keys->strs util.collections/keys->strs)

;; UUID utilities
(def uuidv7 util.uuid/v7)

;; Vars utilities
(def vname vars/vname)



