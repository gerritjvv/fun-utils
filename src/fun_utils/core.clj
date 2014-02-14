(ns fun-utils.core
 (:require [clojure.core.async :refer [go <! >! <!! >!! alts! chan close! thread timeout go-loop]]
           [clojure.core.async :as async]
           [clj-tuple :refer [tuple]])
 (:import [java.util.concurrent ExecutorService]
          [clojure.lang IFn]))

(defn chan-bridge
  ([ch-source map-f ch-target]
   "map map-f onto ch-source and copy the result to ch-target"
    (chan-bridge (async/map map-f [ch-source]) ch-target))         
  ([ch-source ch-target]
    "in a loop read from ch-source and write to ch-target
     this function returns inmediately and returns the ch-target"    
      (go 
         (while (not (Thread/interrupted))
              (if-let [v (<! ch-source)]
                    (>! ch-target v))))
      ch-target))

 (defn apply-get-create [m k f create-f & args]
   "Get a value from a map m using key k and apply the function f to the value
    f must have signuture  to be applied as (apply f v args)
    m is returned, 
    if the key does not exist in the map the value is created using (apply creawte-f args)
    and then applied to the function f, assoc'ed to the map and the new map is returned.

    This utility function is useful escecially when used with agents, refs, atoms and in loops"
   (if-let [v (get m k)]
     (do
       (apply f v args)
       m)
     (let [v (apply create-f args)]
       (apply f v args)
       (assoc m k v))))


(defn go-seq 
  ([f ch]
  "Waits in a goo loop with (<! ch) if v is nil f is not called and the loop is not recurred
   otherwise if is called as (f ch)"
   (go-loop []
     (if-let [v (<! ch)]
       (do 
         (f v)
         (recur)))))
  ([f ch & chs]
	  "Waits in a go loop with alts! chs, if a result is attained
	   f is called as (f v ch) if f returns false the loop is not recurred"
   (let [chs2 (cons ch chs)]
		  (go-loop []
		    (let [[v ch] (alts! chs2)]
		      (if (f v ch)
		        (recur)))))))
  
(defn star-channel [& {:keys [master-buff buff wait-response] :or {master-buff 100 buff 100 wait-response false}}]
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
		(let [master-ch (chan master-buff) 
		      create-ch (fn [& args]
                      (let [ch (chan buff)]
										    (go ;we use thread here because of bug in go, with a go here two or more threads may run the go block at the same time
                          (loop []
												      (when-let [ch-v (<! ch)]
												        (let [[resp-ch f & args] ch-v
		                                  v
					                              (try
		                                     (apply f args)
					                               (catch Exception e (do (.printStackTrace ^Exception e) (throw (RuntimeException. (str "Error while applying " f " to " args " err: " e))))))
					                             ]
		                              (if resp-ch
		                                (>! resp-ch (if v v [])))
                                  (recur)))))
                              
										    ch))
         close-channel (fn [ch-map key-val]
                         (if-let [ch (get ch-map key-val)]
                           (close! ch)))
         
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
          close-f     (fn [& args]
                        (close! master-ch))]
					(go 
					  (loop [ch-map {}]
              (if-let [ch-v (<!! master-ch)]
                (let [
                      [key-val resp-ch f args] ch-v
						          ch-map2 (cond 
                                  (= f :remove)
                                  (apply-command :remove ch-map key-val)
                                  :else 
			                              
                                     (if-let [ch (get ch-map key-val)]
                                        (if (coll? f)
                                            (let [[command f-n] f]
                                                 ;apply a function then then the command, this allows us to send a function and remove a key in the same transaction
                                                 (>! ch (tuple resp-ch f-n args))
                                                 (apply-command command ch-map key-val)) 
                                            (do 
                                                (>! ch (tuple resp-ch f args))
                                                ch-map))
                                        
                                        (let [ch (create-ch)
                                              ch-map3 (assoc ch-map key-val ch)] ;;this is the duplicate of above, but >! does not work behind functions :(
                                          (if (coll? f)
                                            (let [[command f-n] f]
                                                 (>! ch (tuple resp-ch f-n args))
                                                 (apply-command command ch-map3 key-val)) 
                                            (do 
                                                (>! ch (tuple resp-ch f args))
                                                ch-map3)))))
                                    
						                                      ]
                    
                    (recur ch-map2))
                    (doseq [[key-val ch] ch-map]
                      (close! ch))
                )))
		   {:send star-channel-f :close close-f}
 		   ))
		            

(defn buffered-select [f-select init-pos]
  "Creates a lazy sequence of messages for this datasource"
  (letfn [
           (m-seq [buff pos]
                   (let [buff2 (if (empty? buff) (f-select pos) buff)]
                         (cons (first buff2) (lazy-seq (m-seq (rest buff2) (inc pos) )))
                     )
                   )
           ]
    (m-seq nil init-pos)
    )
  )

(defn stop-fixdelay [ch]
  (close! ch))

(defmacro fixdelay [ ms & body]
  "Runs the body every ms after the last appication of body completed"
  `(let [close-ch# (chan)]
          (go (loop [] 
                 (let [[v# ch#] (alts! [close-ch# (timeout ~ms)])]
                   (if (not (= close-ch# ch#))
                    (do 
                      ~@body 
                      (recur))))))
          close-ch#))

(defn submit [^ExecutorService service ^IFn f]
  "Helper function to type hint a function to a runnable, avoiding reflection
   when submitting to a thread"
  (let [^Runnable r f]
    (.submit service r)))


(defn merge-distinct-vects[v1 v2]
  "
   Merge the two vectors with only distinct elements remaining,
   such that (sort (merge-distinct-vects [1 2 5 6 3] [1 2 8 9])) => [1 2 3 5 6 8 9]
  "
  (if (empty? v2)
    v1
    (-> (apply conj v1 v2) set vec)))

(defn always-false 
      ([] (tuple false nil))
      ([prev v] (tuple false nil)))

(defn buffered-chan 
  "Reads from ch-source and if either timeout or the buffer-count has been
   read the result it sent to the channel thats returned from this function"
  ([ch-source buffer-count timeout-ms]
    (buffered-chan ch-source buffer-count timeout-ms 1))
  ([ch-source buffer-count timeout-ms buffer-or-n]
    (buffered-chan ch-source buffer-count timeout-ms buffer-or-n always-false))
  ([ch-source buffer-count timeout-ms buffer-or-n check-f]
    "check-f must be callable with zero and two arguments and returns a tuple of [boolean-val state]
     the state variable will be given to check-f when its called next together with the vector buffer,
     this allows check-f to accumulate counts etc, see always-false"
    
    (let [ch-target (chan buffer-or-n)]
      (go
        (loop [buff [] t (timeout timeout-ms) prev-v (check-f)]
          (let [[v ch] (alts! [ch-source t])
                b (if v (conj buff v) buff)]
            (if (or (= ch t) (not (nil? v))) 
	            (let [[break? prev-2] (check-f prev-v b)]
               (if (or (>= (count b) buffer-count) (not v) break?)
	              (do
	                (if (> (count b) 0)
	                  (>! ch-target b)) ;send the buffer to the channel
	                (recur [] (timeout timeout-ms) prev-2))) ;create a new buffer and new timeout
	              (recur b t prev-2));pass the new buffer and the current timeout
             (do ;on loop exit, if anything in the buffer send it
               (if (> (count b) 0)
                   (>! ch-target b)))
             )))) 
            ch-target)))
  
  
