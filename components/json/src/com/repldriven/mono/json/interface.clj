(ns com.repldriven.mono.json.interface
  "Reading and writing JSON, as operations rather than as a library.

  The point of this brick is that `clojure.data.json` is an
  implementation detail. Its four functions name what they do — parse
  a string, parse a stream, and the two inverses — and every JSON
  library on the JVM has all four, so swapping the one underneath is
  a change here and nowhere else.

  Two rules keep that true, and they are the reason this brick stays
  small:

  - Nothing library-specific is re-exported. Notably not
    `clojure.data.json/JSONWriter`: extending it is how you teach
    data.json about a new type, but cheshire uses `add-encoder` and
    jsonista uses ObjectMapper modules, so exposing the protocol
    would weld every caller to today's choice. If custom encoding is
    ever needed, it wants a neutral registration function here.
  - `options` are passed through to the underlying library, so any
    call that uses them is, to that extent, tied to it. `:key-fn` is
    the only one in use and every library has an equivalent.

  Parse and serialize failures come back as `:json/parse` and
  `:json/serialize` anomalies."
  (:refer-clojure :exclude [read])
  (:require
    [com.repldriven.mono.json.core :as core]))

(defn read-str
  "Parse a JSON string into Clojure data, or return an anomaly. Keys
  are strings unless `:key-fn` says otherwise.

  Args:
  - s: the JSON string.
  - options: parser options, passed to the underlying library."
  [s & {:as options}]
  (apply core/read-str s (apply concat options)))

(defn read
  "Parse JSON from a `java.io.Reader`, or return an anomaly. For a
  body or file large enough that holding it as a string is the wrong
  shape.

  Args:
  - reader: a `java.io.Reader`.
  - options: parser options, passed to the underlying library."
  [reader & {:as options}]
  (apply core/read reader (apply concat options)))

(defn write-str
  "Serialize Clojure data to a JSON string, or return an anomaly.

  Args:
  - x: the value to serialize.
  - options: writer options, passed to the underlying library."
  [x & {:as options}]
  (apply core/write-str x (apply concat options)))

(defn write
  "Serialize Clojure data as JSON to a `java.io.Writer`, or return an
  anomaly. Writes as it goes, rather than building the whole string
  first.

  Args:
  - x: the value to serialize.
  - writer: a `java.io.Writer`.
  - options: writer options, passed to the underlying library."
  [x writer & {:as options}]
  (apply core/write x writer (apply concat options)))
