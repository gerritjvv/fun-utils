(ns fun-utils.queue
  (:import
    [java.util.concurrent ArrayBlockingQueue TimeUnit]
    [org.jctools.queues SpmcArrayQueue MpmcArrayQueue]
    (java.util ArrayList List)))


(defprotocol IQueue
  (-offer! [this data] [this data timeout-ms])
  (-poll!  [this] [this timeout-ms])
  (-drain! [this n])
  (-size   [this] "Size of the queue"))

(defn- _take!
  "
  Helper function to simulate blocking for queues that do not directly support it
  Will block the current thread till a value is available"
  [queue ^long timeout-ms]
  (if-let [v (-poll! queue)]
    v
    (let [start-wait-time (System/currentTimeMillis)]
      (loop [v1 (-poll! queue)]
        (if v1
          v1
          (when (< (- (System/currentTimeMillis) start-wait-time) timeout-ms)
            (Thread/yield)
            (recur (-poll! queue))))))))

(defn- _put!
  "Helper function to simulate blocking for queues that do not directly support it
   Will block the current thread till a value can be placed on the queue"
  [queue data ^long timeout-ms]
  (if (-offer! queue data)
    true
    (let [start-wait-time (System/currentTimeMillis)]
      (loop [v1 (-offer! queue data)]
        (if v1
          true
          (when (< (- (System/currentTimeMillis) start-wait-time) timeout-ms)
            (Thread/yield)
            (recur (-offer! queue data))))))))

(defn offer!
  "Non blocking put on queue, if successfull return true, or wait timeout-ms for a value to appear"
  ([queue data] (-offer! queue data))
  ([queue data timeout-ms] (-offer! queue data timeout-ms)))
(defn poll!
  "Non blocking get on queue, if no value returns nil, or wait timeout-ms for the action to complete"
  ([queue] (-poll! queue))
  ([queue timeout-ms] (-poll! queue timeout-ms)))

(defn size   [queue]      (-size queue))

(defmulti queue-factory (fn [t limit & args] t))

(defmethod queue-factory :array-queue [_ limit & _]
  (let [^ArrayBlockingQueue queue (ArrayBlockingQueue. (int limit))]
    (reify IQueue
      (-offer!   [_ data timeout-ms] (.offer queue data timeout-ms TimeUnit/MILLISECONDS))
      (-offer! [_ data] (.offer queue data))
      (-drain! [_ n] (let [^List l (ArrayList. (int n))]
                       (.drainTo queue l (int n))
                       l))
      (-poll!  [_ timeout-ms] (.poll queue timeout-ms TimeUnit/MILLISECONDS))
      (-poll!  [_]      (.poll queue))
      (-size   [_]      (.size queue)))))

(defmethod queue-factory :spmc-array-queue [_ limit & _]
  (let [^SpmcArrayQueue queue (SpmcArrayQueue. (int limit))]
    (reify IQueue
      (-offer!   [this data timeout-ms] (_put! this data timeout-ms))
      (-offer! [_ data] (.offer queue data))
      (-drain! [_ n] (if-let [o (.poll queue)]
                       [o]
                       []))
      (-poll!  [this timeout-ms] (_take! this timeout-ms))
      (-poll!  [_]      (.poll queue))
      (-size   [_]      (.size queue)))))

(defmethod queue-factory :mpmc-array-queue [_ limit & _]
  (let [^MpmcArrayQueue queue (MpmcArrayQueue. (int limit))]
    (reify IQueue
      (-offer!   [this data timeout-ms] (_put! this data timeout-ms))
      (-offer! [_ data] (.offer queue data))
      (-drain! [_ n] (if-let [o (.poll queue)]
                       [o]
                       []))
      (-poll!  [this timeout-ms] (_take! this timeout-ms))
      (-poll!  [_]      (.poll queue))
      (-size   [_]      (.size queue)))))


