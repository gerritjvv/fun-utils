(ns fun-utils.chtest
 (:require [clojure.core.async :refer [go <! >! <!! >!! chan close! thread timeout]]
           [clojure.core.async :as async]
           [clj-tuple :refer [tuple]])
 (:import [java.util.concurrent ExecutorService]
          [clojure.lang IFn]))

 
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
           create-ch 
              (fn [& args]
                    (let [ch (chan buff)]
		     (go 
                        (loop []
			    (when-let [ch-v (<! ch)]
			      (let [[resp-ch f & args] ch-v
                                    v
				     (try
		                        (apply f args)
					(catch Exception e (throw (RuntimeException. (str "Error while applying " f " to " args " err: " e)))))
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
              (if-let [ch-v (<! master-ch)]
                (let [[key-val resp-ch f args] ch-v
	              ch-map2 (cond 
                                  (= f :remove)
                                  (apply-command :remove ch-map key-val)
                                  :else 
			                              
                                     (if-let [ch (get key-val ch-map)]
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
                      (close! ch)))))
		   {:send star-channel-f :close close-f}
 		   ))
		            

(defn submit [^ExecutorService service ^IFn f]
  "Helper function to type hint a function to a runnable, avoiding reflection
   when submitting to a thread"
  (let [^Runnable r f]
    (.submit service r)))

         
(defn test-star []
  (let [ {:keys [send close]} (star-channel)
       base-dir (doto (java.io.File. "~/concurrent") (.mkdirs))
       file-a (doto (java.io.File. base-dir "file-a") (.delete) (.createNewFile))
       out  (atom (java.io.DataOutputStream. (java.io.FileOutputStream. file-a)))
       exec (java.util.concurrent.Executors/newCachedThreadPool)]
    
    (prn "start with " @out)
		(dotimes [i 10]
		          (submit exec #(send :a (fn [f] 
                                     (.writeInt @out i)
                                     
                                     (if (or (= i 7) (= i 7))
                                       (let[_ (.close @out)
                                            o (java.io.DataOutputStream. (java.io.FileOutputStream. file-a true))
                                            f (fn [a] o)]
                                           (prn "new output " o)
                                           (swap! out f)))
                                     
                                     ) file-a) ))
		
		(doto exec
		          (.shutdown)
		          (.awaitTermination 10 java.util.concurrent.TimeUnit/SECONDS))
    
  
    )))


