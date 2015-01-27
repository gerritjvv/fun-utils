(ns fun-utils.core
  (:import (java.util.concurrent ExecutorService)
           (clojure.lang IFn)
           (java.util ArrayList)
           (java.util.concurrent.atomic AtomicReference AtomicLong AtomicBoolean))
  (:require [clojure.core.async :refer [go <! >! <!! >!! alts! alts!! chan close! thread timeout go-loop]]
            [clojure.core.async :as async]
            [clojure.tools.logging :refer [info error]]
            [clj-tuple :refer [tuple]]))

(defn chan-bridge
  "in a loop read from ch-source and write to ch-target
     this function returns inmediately and returns the ch-target"
  ([ch-source map-f ch-target]
   (chan-bridge (async/map map-f [ch-source]) ch-target))
  ([ch-source ch-target]
   (go
     (while (not (Thread/interrupted))
       (if-let [v (<! ch-source)]
         (>! ch-target v))))
   ch-target))

(defn apply-get-create
  "Get a value from a map m using key k and apply the function f to the value
   f must have signuture  to be applied as (apply f v args)
   m is returned,
   if the key does not exist in the map the value is created using (apply creawte-f args)
   and then applied to the function f, assoc'ed to the map and the new map is returned.

   This utility function is useful escecially when used with agents, refs, atoms and in loops"
  [m k f create-f & args]
  (if-let [v (get m k)]
    (do
      (apply f v args)
      m)
    (let [v (apply create-f args)]
      (apply f v args)
      (assoc m k v))))



(defn thread-seq
  "  This function is the same as go-seq with the exception that it runs in a thread.
     If you are running IO or blocking code its important to not use go blocks.
     The thread will exit when the channel is closed.
     Waits in a thread loop with alts!! chs, if a result is attained
	   f is called as (f v ch) if f returns false the loop is not recurred"
  ([f ch]
   (thread
     (loop []
       (if-let [v (<!! ch)]
         (do
           (f v)
           (recur))))))
  ([f ch & chs]
   (let [chs2 (cons ch chs)]
     (thread
       (loop []
             (let [[v ch] (alts!! chs2)]
               (if (f v ch)
                 (recur))))))))

(defn go-seq
  "Waits in a go loop with alts! chs, if a result is attained
	   f is called as (f v ch) if f returns false the loop is not recurred"
  ([f ch]
   (go-loop []
            (if-let [v (<! ch)]
              (do
                (f v)
                (recur)))))
  ([f ch & chs]
   (let [chs2 (cons ch chs)]
     (go-loop []
              (let [[v ch] (alts! chs2)]
                (if (f v ch)
                  (recur)))))))

(defn star-channel
  "Creates a start channel with a dispatcher channel and a workder channel per key
     A map of two functions are returned, on send, and the other close.
     Send has signuture (dispatch-key, function, function-args)
     Close takes no arguments and close all channels
     If wait-response is true calls to send will block til the function returns, then function waits for execution
     on a single channel identified by the key-val, i.e all functions for the same key will be run
     synchronously,
     if wait-response if false, the send function will return inmediately (only if the master channel was not full)
     if nil is returned the function returns [], this is because we can't return nil on a channel
     "
  [& {:keys [master-buff buff wait-response] :or {master-buff 100 buff 100 wait-response false}}]
  (let [master-ch (chan master-buff)
        create-ch (fn [& args]
                    (let [ch (chan buff)
                          ^AtomicLong status (AtomicLong.)]
                      (thread                                   ;we use thread here because of bug in go, with a go here two or more threads may run the go block at the same time
                        (loop []
                          (if-let [ch-v (<!! ch)]
                            (let [[resp-ch f & args] ch-v
                                  v
                                  (try
                                    (apply f args)          ;we cannot throw any exception here, otherwise the channel will exit and we create a deadlock in the master loop
                                    (catch Exception e (do (error e e))))
                                  ]
                              (if resp-ch
                                (>!! resp-ch (if v v [])))
                              (.incrementAndGet status)
                              (recur))
                            (.set status -100))))

                      [status ch]))
        close-channel (fn [ch-map key-val]
                        (if-let [ch (get ch-map key-val)]
                          (close! (second ch))))

        apply-command (fn [command ch-map key-val]
                        (cond
                          (= command :remove)
                          (do
                            (close-channel ch-map key-val)
                            (dissoc ch-map key-val))
                          :else
                          ch-map))

        star-channel-f (fn star-channel-f
                         ([key-val f args]
                          (star-channel-f wait-response key-val f args))
                         ([wait-response2 key-val f args]
                          (if wait-response2
                            (let [resp-ch (chan)]
                              (>!! master-ch (tuple key-val resp-ch f args))
                              (<!! resp-ch))
                            (>!! master-ch (tuple key-val nil f args)))))
        close-f (fn [& args]
                  (close! master-ch))
        master-activity (AtomicLong. 0)
        master-exit (AtomicBoolean. false)

        ch-map-view (AtomicReference. nil)]
    (thread
      (loop [ch-map {}]
        ;some bug here in channels causes us to use <!! instead of <! i.e using <! returns nil always and <!! return the value expected
        (if-let [ch-v (<!! master-ch)]
          (let [m
                (try
                  (let [ [key-val resp-ch f args] ch-v
                         ch-map2 (cond
                                   (= f :remove)
                                   (apply-command :remove ch-map key-val)
                                   :else
                                   (if-let [ch (get ch-map key-val)]
                                     (if (coll? f)
                                       (let [[command f-n] f]
                                         ;apply a function then then the command, this allows us to send a function and remove a key in the same transaction
                                         (>!! (second ch) (tuple resp-ch f-n args))
                                         (apply-command command ch-map key-val))
                                       (do
                                         (>!! (second ch) (tuple resp-ch f args))
                                         ch-map))

                                     (let [ch (create-ch)
                                           ch-map3 (assoc ch-map key-val ch)] ;;this is the duplicate of above, but >! does not work behind functions :(
                                       (if (coll? f)
                                         (let [[command f-n] f]
                                           (>!! (second ch) (tuple resp-ch f-n args))
                                           (apply-command command ch-map3 key-val))
                                         (do
                                           (>!! (second ch) (tuple resp-ch f args))
                                           ch-map3)))))]
                    ;we use this for testing introspection and tooling to allow viewing what keys are in the star map
                    ;but this is not an atom nor a ref its only a view
                    (.set ^AtomicReference ch-map-view ch-map2)
                    ch-map2)
                  (catch Exception e (do (error e e) ch-map)))]
            (.incrementAndGet ^AtomicLong master-activity)
            (recur m))
          (do
            (info "Closing star channel")
            (.set master-exit true)
            (doseq [[key-val ch] ch-map]
              (close! (second ch)))))))
    {:send star-channel-f :close close-f :ch-map-view ch-map-view :master-actitivy master-activity :master-exit master-exit}))


(defn buffered-select
  "Creates a lazy sequence of messages for this datasource"
  [f-select init-pos]
  (letfn [(m-seq [buff pos]
                 (let [buff2 (if (empty? buff) (f-select pos) buff)]
                   (cons (first buff2) (lazy-seq (m-seq (rest buff2) (inc pos))))))]
    (m-seq nil init-pos)))

(defn stop-fixdelay [ch]
  (close! ch))

(defmacro fixdelay
  "Runs the body every ms after the last appication of body completed"
  [ms & body]
  `(let [close-ch# (chan)]
     (go (loop []
           (let [[v# ch#] (alts! [close-ch# (timeout ~ms)])]
             (if (not (= close-ch# ch#))
               (do
                 ~@body
                 (recur))))))
     close-ch#))

(defmacro fixdelay-thread
  "Runs the body every ms after the last appication of body completed, the code is run in a separate Thread
   Returns a channel that when passed to stop-fixdelay will close this thread"
  [ms & body]
  `(let [close-ch# (chan)]
     (thread
       (loop []
           (let [[v# ch#] (alts!! [close-ch# (timeout ~ms)])]
             (if (not (= close-ch# ch#))
               (do
                 ~@body
                 (recur))))))
     close-ch#))

(defn submit
  "Helper function to type hint a function to a runnable, avoiding reflection
   when submitting to a thread"
  [^ExecutorService service ^IFn f]
  (let [^Runnable r f]
    (.submit service r)))


(defn merge-distinct-vects
  "
   Merge the two vectors with only distinct elements remaining,
   such that (sort (merge-distinct-vects [1 2 5 6 3] [1 2 8 9])) => [1 2 3 5 6 8 9]
  "
  [v1 v2]
  (if (empty? v2)
    v1
    (-> (apply conj v1 v2) set vec)))

(defn always-false
  ([] (tuple false nil))
  ([prev v] (tuple false nil)))

(defn aconj! [^ArrayList a v]
  (.add a v)
  a)

(defn asize [^ArrayList a] (.size a))
(defn aclear! [^ArrayList a] (.clear a) a)

(defn buffered-chan
  "Reads from ch-source and if either timeout or the buffer-count has been
   read the result it sent to the channel thats returned from this function
   check-f must be callable with zero and two arguments and returns a tuple of [boolean-val state]
   the state variable will be given to check-f when its called next together with the vector buffer,
   this allows check-f to accumulate counts etc, see always-false
  "
  ([ch-source buffer-count timeout-ms]
   (buffered-chan ch-source buffer-count timeout-ms 1))
  ([ch-source buffer-count timeout-ms buffer-or-n]
   (buffered-chan ch-source buffer-count timeout-ms buffer-or-n always-false))
  ([ch-source buffer-count timeout-ms buffer-or-n check-f]

   (let [ch-target (chan buffer-or-n)]
     (go
       (loop [buff (ArrayList.) t (timeout timeout-ms) prev-v (check-f)]
         (let [[v ch] (alts! [ch-source t])
               b (if v (aconj! buff v) buff)]
           (if (and (not (= ch t)) (nil? v))
             (do                                            ;on loop exit, if anything in the buffer send it
               (if (> (asize b) 0)
                 (>! ch-target (vec b))))
             (let [[break? prev-2] (check-f prev-v b)
                   [b2 t2] (if (or (>= (asize b) buffer-count) (not v) break?)
                             (do
                               (if (> (asize b) 0)
                                 (>! ch-target (vec b)))          ;send the buffer to the channel
                               [(aclear! b) (timeout timeout-ms)])
                             [b t])]
               ;create a new buffer and new timeout
               (recur b2 t2 prev-2))                        ;pass the new buffer and the current timeout
             ))))
     ch-target)))
  
  
