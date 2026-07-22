(ns com.repldriven.mono.scheduler.core
  (:require
    [cronut :as cronut]
    [cronut.trigger :as trigger]
    [com.repldriven.mono.log.interface :as log])
  (:import
    (java.util Date TimeZone)
    (org.quartz CronExpression Job)))

;; All jobs we register share one Quartz group; the caller-supplied `id`
;; is the job name (unique within the group). cronut/Quartz keys a job by
;; (name, group).
(def ^:private group "scheduler")

(defn start
  "Create and start an in-memory cronut (Quartz) scheduler. Returns the
  scheduler, which is passed back to `schedule!` / `unschedule!` / `stop`."
  []
  (cronut/start
   (cronut/scheduler {:concurrent-execution-disallowed? true
                      :update-check? false})))

(defn stop
  [scheduler]
  (when scheduler (cronut/shutdown scheduler))
  nil)

(defn- handler-job
  "A Quartz Job that fires `handler-fn` (0-arg). cronut's job factory
  returns this very instance, so the closure is preserved across fires.
  A throwing handler is logged, never propagated to the scheduler."
  [handler-fn]
  (reify
   Job
     (execute [_ _job-context]
       (try
         (handler-fn)
         (catch Throwable t
           (log/error t "Scheduled handler threw"))))))

(defn schedule!
  "Register the job named `id` to fire `handler-fn` on the Quartz cron
  expression `cron-expr`, replacing any existing job of that name (so this
  doubles as reschedule). Returns `id`."
  [scheduler id cron-expr handler-fn]
  ;; deleteJob is a no-op (returns false) when the key is absent, so this
  ;; is a safe replace: drop the old job + its triggers, then re-add.
  (cronut/delete-job scheduler id group)
  (cronut/schedule-job scheduler
                       (trigger/cron cron-expr)
                       (handler-job handler-fn)
                       {:name id :group group :durable? true})
  id)

(defn unschedule!
  "Remove the job named `id` (and its trigger). Safe if absent."
  [scheduler id]
  (cronut/delete-job scheduler id group)
  nil)

(defn next-fire-at
  "Epoch millis of the next time `cron-expr` fires strictly after
  `after-millis` (interpreted in UTC), or nil if it never fires again.
  Pure — evaluates the cron expression directly, no scheduler needed."
  [cron-expr after-millis]
  (let [expr (doto (CronExpression. ^String cron-expr)
               (.setTimeZone (TimeZone/getTimeZone "UTC")))]
    (some-> (.getNextValidTimeAfter expr (Date. (long after-millis)))
            .getTime)))
