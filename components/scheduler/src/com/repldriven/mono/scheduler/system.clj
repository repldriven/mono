(ns com.repldriven.mono.scheduler.system
  (:require
    [com.repldriven.mono.scheduler.core :as core]
    [com.repldriven.mono.system.interface :as system]))

;; Owns the cronut (Quartz) scheduler lifecycle. The started scheduler is
;; the component instance; consumers `!system/ref scheduler.scheduler` and
;; register their triggers against it via the interface.
(def ^:private scheduler
  {:system/start (fn [{:system/keys [instance]}] (or instance (core/start)))
   :system/stop (fn [{:system/keys [instance]}] (core/stop instance))
   :system/instance-schema some?})

(system/defcomponents :scheduler {:scheduler scheduler})
