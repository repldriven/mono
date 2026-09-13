(ns com.repldriven.mono.scheduler.core
  (:require
    [com.repldriven.mono.error.interface :refer [try-nom]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.utility.interface :as util]

    [cronut :as cronut]
    [cronut.job :as job]
    [cronut.trigger :as trigger])
  (:import
    (java.util Date Properties TimeZone)
    (org.quartz CronExpression Job Scheduler)
    (org.quartz.impl StdSchedulerFactory)))

;; All jobs we register share one Quartz group; the caller-supplied `id`
;; is the job name (unique within the group). cronut/Quartz keys a job by
;; (name, group).
(def ^:private group "scheduler")

(defn- properties
  "Quartz properties for one scheduler. StdSchedulerFactory keys its
  repository by instance name, so a fresh name per call is what makes
  each start its own scheduler. The rest is what Quartz's bundled
  defaults would set, which an explicit Properties does not inherit."
  ^Properties [instance-name]
  (doto (Properties.)
    (.setProperty "org.quartz.scheduler.instanceName" instance-name)
    (.setProperty "org.quartz.scheduler.skipUpdateCheck" "true")
    (.setProperty "org.quartz.threadPool.threadCount" "10")))

(defn start
  "Create and start an in-memory cronut (Quartz) scheduler of its own.
  Returns the scheduler, which is passed back to `schedule` /
  `unschedule` / `stop`.

  Not cronut's `scheduler`, which returns Quartz's JVM-wide default:
  every start in a JVM would share one scheduler, and any stop would
  shut it down under the rest. Two systems in one JVM, or two tests,
  each get their own."
  []
  (try-nom
   :scheduler/start
   "Failed to start scheduler"
   (let [name (str "scheduler-" (util/random-suffix 12))
         ^Scheduler scheduler (.getScheduler (StdSchedulerFactory.
                                              (properties name)))]
     ;; What cronut's `scheduler` sets on the default: every job runs
     ;; with DisallowConcurrentExecution, and jobs are built from the
     ;; instance registered rather than a class.
     (.put (.getContext scheduler) "concurrentExecutionDisallowed?" "true")
     (.setJobFactory scheduler (job/factory))
     (cronut/start scheduler))))

(defn stop
  [scheduler]
  (try-nom :scheduler/stop
           "Failed to stop scheduler"
           (do (when scheduler (cronut/shutdown scheduler)) nil)))

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

;; Cron expressions come from configuration, so a malformed one is input
;; rather than a bug: Quartz throws ParseException for bad syntax and
;; IllegalArgumentException for nil.
(defn schedule
  "Register the job named `id` to fire `handler-fn` on the Quartz cron
  expression `cron-expr`, replacing any existing job of that name (so this
  doubles as reschedule). Returns `id`."
  [scheduler id cron-expr handler-fn]
  (try-nom
   :scheduler/schedule
   "Failed to schedule job"
   ;; deleteJob is a no-op (returns false) when the key is absent, so this
   ;; is a safe replace: drop the old job + its triggers, then re-add.
   (do (cronut/delete-job scheduler id group)
       (cronut/schedule-job scheduler
                            (trigger/cron cron-expr)
                            (handler-job handler-fn)
                            {:name id :group group :durable? true})
       id)))

(defn unschedule
  "Remove the job named `id` (and its trigger). Safe if absent."
  [scheduler id]
  (try-nom :scheduler/unschedule
           "Failed to unschedule job"
           (do (cronut/delete-job scheduler id group) nil)))

(defn next-fire-at
  "Epoch millis of the next time `cron-expr` fires strictly after
  `after-millis` (interpreted in UTC), or nil if it never fires again.
  Pure — evaluates the cron expression directly, no scheduler needed."
  [cron-expr after-millis]
  (try-nom :scheduler/next-fire-at
           "Failed to evaluate cron expression"
           (let [expr (doto (CronExpression. ^String cron-expr)
                        (.setTimeZone (TimeZone/getTimeZone "UTC")))]
             (some-> (.getNextValidTimeAfter expr (Date. (long after-millis)))
                     .getTime))))
