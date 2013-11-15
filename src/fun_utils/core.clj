(ns fun-utils.core
 (:require [clojure.core.async :refer [go <! >! <!! >!! chan close! timeout]])
 (:import [java.util.concurrent ExecutorService]
          [clojure.lang IFn]))



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

(defn star-channel [& {:keys [master-buff buff] :or {master-buff 100 buff 100}}]
    "Creates a start channel with a dispatcher channel and a workder channel per key
     A map of two functions are returned, on send, and the other close.
     Send has signuture (dispatch-key, function, function-args)
     Close takes no arguments and close all channels
     Calls to send will block til the function returns, then function waits for execution
     on a single channel identified by the key-val, i.e all functions for the same key will be run
     synchronously, 
     if nil is returned the function returns [], this is because we can't return nil on a channel"
		(let [master-ch (chan master-buff) 
		      create-ch (fn [& args]
										  (let [ch (chan buff)]
										    (go
										      (while (not (Thread/interrupted))
										        (let [[resp-ch f & args] (<! ch)
                                  v
			                              (try
                                       (apply f args)
			                               (catch Exception e (throw (RuntimeException. (str "Error while applying " f " to " args " err: " e)))))
			                             ]
                              (>! resp-ch (if v v [])))))
										    ch))
		      star-channel-f (fn [key-val f args]
												  (let [resp-ch (chan)]
												    (>!! master-ch [key-val resp-ch f args])
												    (<!! resp-ch)))
          close-f     (fn [& args]
                        (close! master-ch))]
					(go 
					  (loop [ch-map {}]
					    (let [[key-val resp-ch f args] (let [v (<! master-ch)] v)
					          ch-map2 (apply-get-create ch-map 
					                              key-val 
					                              (fn [ch args] (go (>! ch [resp-ch f args]))) 
					                              create-ch
					                              args)
					                                      ]
							    (recur ch-map2))))
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

(defmacro fixdelay [ ms & body]
  "Runs the body every ms after the last appication of body completed"
          `(go (loop [] (<! (timeout ~ms)) ~@body (recur))))

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