(ns fun-utils.threads
  (:require [clojure.tools.logging :refer [error info]]
            [clojure.core.async :refer [alts!!]])
  (:import (java.util.concurrent Executors ArrayBlockingQueue ThreadPoolExecutor ExecutorService ThreadPoolExecutor$CallerRunsPolicy TimeUnit)))


;; A package that allows listening on channels using real threads and only react on events
;; in a shared pool of threads.


(defn ^ExecutorService create-exec-service [threads]
  (let [queue (ArrayBlockingQueue. (int 2))
        exec  (doto (ThreadPoolExecutor. 0 threads 60 TimeUnit/SECONDS queue)
                (.setRejectedExecutionHandler  (ThreadPoolExecutor$CallerRunsPolicy.)))]
    exec))

(defn submit [^ExecutorService service ^Runnable r]
  (.submit service r))

(defn send-to-fun [service ch v fun-map]
  (if-let [f (get fun-map ch)]
    (submit service (fn []
                      (try
                        (f v)
                        (catch Exception e (error e e)))))))

(defn listen [{:keys [chans-ref fun-map-ref]} ch f]
  {:pre [(fn? f) chans-ref fun-map-ref]}
  (dosync
    (alter fun-map-ref assoc ch f)
    (alter chans-ref (fn [v]
                       (vec (set (conj v ch))))))
  ch)

(defn stop-listen
  "Remember to close ch if you are not going to use it"
  [{:keys [chans-ref fun-map-ref]} ch]
  {:pre [chans-ref fun-map-ref ch]}
  (dosync
    (alter fun-map-ref dissoc ch)
    (alter chans-ref (fn [v]
                       (-> v set (disj ch) vec))))
  ch)

(defn close! [{:keys [^ExecutorService executor]} & {:keys [timeout-ms] :or {timeout-ms 2000}}]
  (.shutdown executor)
  (if-not (.awaitTermination executor (long timeout-ms) TimeUnit/MILLISECONDS)
    (.shutdownNow executor)))

(defn shared-threads [threads]
  (let [executor (create-exec-service threads)
        chans-ref (ref [])
        fun-map-ref (ref {})
        ctx  {:executor executor :chans-ref chans-ref :fun-map-ref fun-map-ref}]
    (submit executor
            (fn []
              (while (not (.isInterrupted (Thread/currentThread)))
                (try
                  (let [chans @chans-ref]
                    (if-not (empty? chans)
                      (do
                        (let [[v ch] (alts!! chans)]
                          (if v
                            (send-to-fun executor ch v @fun-map-ref)
                            (stop-listen ctx ch))))
                      (Thread/sleep 500)))
                  (catch InterruptedException e nil)
                  (catch Exception e (error e e))))
              (info "<<<<<<<<<<<<<<<<<<<<< Exiting shared-threads loop >>>>>>>>>>>>>>>>>>>>>>")))
   ctx))

