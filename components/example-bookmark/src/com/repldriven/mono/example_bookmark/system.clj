(ns com.repldriven.mono.example-bookmark.system
  (:require
    [com.repldriven.mono.system.interface :as system]))

(def ^:private bookmark-store
  {:system/start (fn [{:system/keys [config instance]}] (or instance config))
   :system/config {:record-db system/required-component
                   :record-store system/required-component}
   :system/instance-schema map?})

(system/defcomponents :example-bookmark {:store bookmark-store})
