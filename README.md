# fun-utils

Clojure utility functions that come up time and again while developing clojure software.


## Usage

Best way to see how to use them is going through the unit tests.
I've tried to test each utlity function in its own test file.

[![Clojars Project](http://clojars.org/fun-utils/latest-version.svg)](http://clojars.org/fun-utils)

## Queue

The ```fun-utils.queue``` interface provides a basic protocol ```IQueue``` that encapsulates the offer, poll and size functions
most commonly used with queues. It also provides an factory multi method to create different implementations.
 
Wraps queues ArrayBlockingQueue from java.util.concurrent and SpmcArrayQueue, and MpmcArrayQueue from https://github.com/JCTools/JCTools

```clojure
(require '[fun-utils.queue :as queue] :reload-all)

;;create a ArrayBlockingQueue
(def q (queue/queue-factory :array-queue 10))
(queue/offer! q 10)
(queue/size q)
;;1
(queue/poll! q)
;;10

;;create a SpmcArrayQueue queue
(def q (queue/queue-factory :spmc-array-queue 10))
(queue/offer! q 11)
(queue/poll! q)
;;11

;;create a MpmcArrayQueue queue
(def q (queue/queue-factory :mpmc-array-queue 10))
(queue/offer! q 12)
(queue/poll! q)
;; 12

```

## cache

The ```fun-utils.cache``` namespace provides a Clojure Map wrapper arround the Google's Guava Cache.
It gives us all of the perfromance and usibility of the Guava Cache builder but with the convinience of
acting as a Clojure map.

I've found that the guava cache with expire-on-write is much more reliable and performant than using Clojure's
own ttl cache.

```clojure

(require '[fun-utils.cache :as c])

(def cache (c/create-cache))
(assoc cache :a 1)
(get cache :a)
;; 1

(def cache-ttl (c/create-cache :expire-after-write 500))
(get cache-ttl :a)
;; nil
(assoc cache-ttl :a 1)
(get cache-ttl :a)
;; 1
(Thread/sleep 1000)
(get cache-ttl :a)
;; nil

(def cache2 (c/create-loading-cache (fn [k] [k 1])))
(get cache2 1)
;; [1 1]

;;specialised memoize for single arity functions
(def f (c/memoize-1 (fn [x] (Thread/sleep x) x)))
(f 5000)
;; 5000 after waiting for 5 seconds

(f 5000)
;; 5000 immediately

(def f (c/memoize (fn [a v] (Thread/sleep v) v)))
(f :a 5000)
;; 5000 after waiting for 5 seconds

(f :a 5000)
;; 5000 immediately

;;;;;;;async background refresh of caches
;;;;;;; when using :refresh-after-write the guava cache will reload values after N milliseconds
;;;;;;; the load function is called using the default executor (same as for future Agent/soloExecutor)
;;;;;;; a custom java.util.concurrent.ExecutorService can be set on cache creation by setting to 
;;;;;;; *executor* dynamic var, this variable is captured on cache creation
;;;;;;;see https://code.google.com/p/guava-libraries/wiki/CachesExplained#Refresh
;;;;;;     https://github.com/gerritjvv/fun-utils/issues/1

(import '[java.util.concurrent Executors])
(def exec (Executors/newFixedThreadPool 10))

(defn wait-and-print [[s ms]]
   (println "starting " s " at " (java.util.Date.))
   (Thread/sleep ms)
   (println "finished " s " at " (java.util.Date.) " waited " ms "ms")
   [s ms (java.util.Date.)])
   
(def cache3 (binding [c/*executor* exec] (c/create-loading-cache wait-and-print :refresh-after-write 500)))
;; using the default executor
;;(def cache3 (c/create-loading-cache wait-and-print :refresh-after-write 500))

;;wait one second
(get cache3 ["foo1" 1000])
;;return without waiting
(get cache3 ["foo1" 1000])

```

## fixdelay and fixdelay-thread

Use's clojure.core.async timeout to run an expression every n milliseconds.

Example

```clojure

(def d (fixdelay 1000 (prn "hi")))
;; for IO code use fixdelay-thread
;; => Hi ...  every second

(stop-fixdelay d)
;;stops the fixdelay

```


## go-seq and thread-seq

Note: in all of the examples below go-seq can be changed for thread-seq, use thread-seq if you are running IO code.  
When writing go blocks there is a repeating pattern to avoid 100% cpu spin when the channel is closed.

e.g.
if we run the below code, the loop will spin without pause.

```clojure
(require '[clojure.core.async :refer [go-loop chan <! close!]])
(require '[fun-utils.core :refer [go-seq]])

;;running this code will cause nil to be printed without pause
;;it shows how a bug can be introduced easily by not checking for nil

(def ch (chan 10))
(go-loop []
   (let [v (<! ch)]
     (prn v) (recur)))
     
(close! ch)
```

To avoid this we write

```clojure
(require '[clojure.core.async :refer [go-loop chan <! close!]])
(require '[fun-utils.core :refer [go-seq]])

(def ch (chan 10))
(go-loop []
 (if-let [v (<! ch)]
   (do (prn v) (recur))))
   
(close! ch)
```

To make this pattern easier we can use go-seq like so:

```clojure
(require '[clojure.core.async :refer [go-loop chan <! close!]])
(require '[fun-utils.core :refer [go-seq]])

(def ch (chan 10))

(go-seq #(prn v) ch)
;; go-seq will call the function every time a none nil value is seen on ch
;; if a nil value is seen the loop is terminated

(close! ch)

```

For multiple channels, the function is called as (f v ch) and if f returns false the loop is terminated.

```clojure
(require '[clojure.core.async :refer [go-loop chan <! close!]])
(require '[fun-utils.core :refer [go-seq]])

(def ch1 (chan 10))
(def ch2 (chan 10))
(def ch3 (chan 10))

(go-seq (fn [v ch]
          (if v
           (prn v)
           false)) ch1 ch2 ch3)
           
(close! ch1)
(close! ch2) 
(close! ch3)

```
## Bridge two async channels

###chan-bridge

Copies data from one channel and sends it to another, this is a simple function but one that saves
typing when you just want to copy between channels and optionally apply a function.

Note: clojure.core.async provides pipe that also does bridging.

```clojure

(let [ch1 (chan 10)
      ch2 (chan-bridge ch1 (chan 10))]
           
           (doseq [i (range 5)] (>!! ch1 i))
               
           (prn (reduce + (repeatedly 5 #(<!! ch2)))))
;; 10

(let [ch1 (chan 10)
      ch2 (chan-bridge ch1 inc (chan 10))]
               
      (doseq [i (range 5)] (>!! ch1 i))
               
      (prn (reduce + (repeatedly 5 #(<!! ch2)))))
;; 15
```


## apply-get-create

This function is more useful than it seems at first, mostly with agents, refs, atoms and channels.

It takes a key, function f and create function c-f, if the key exists its value is passed to the f function,
other wise its created with c-f, the value of c-f passed to f and then the value of c-f is assoced to the map,
which is returned after the function call.

Lets say you want to write to N open files based on some key, each file can be contained in an agent,
and the map of agents dynamically grown and shrunk by using a master agent and its value a map of agents.
You can use apply-get-create to do this.

Simple example

```clojure
(use 'fun-utils.core)
(= (apply-get-create {} :a inc (fn [& args] 1) ) {:a 1})
;true

;;using refs
(let [v (ref {})]
             (dosync
               (alter v apply-get-create :a (fn [agnt] (send agnt inc)) (fn [& args] (agent 1)))
               (alter v apply-get-create :a (fn [agnt] (send agnt inc)) (fn [& args] (agent 1)))
               (alter v apply-get-create :a (fn [agnt] (send agnt inc)) (fn [& args] (agent 1))) )
              (prn (:a @v))
              
              (Thread/sleep 500)
              (= (deref (:a @v)) 4)
              
             )
;true

```

## star-channel

The best way to handle concurrency is with no blocking and sending off messages to queues.

There are also some cases where you want to wait for a value 
to come back .e.g reading from a file, or ensure that the calling order is respected.

There is also a need to only block on a certain key, i.e. lets say we have a map of files that we write to, there are two points on concurrency concern,
one is the modification of the map, and the other is the writing to the files.

To solve this we can use a single master channel to create a channel per file, add it to a map, and then write to the file and notify a response via a temporary channel.
It happens also that this pattern can be abstracted to work with connections, dbs and any IO, it also provides implicit serialization of transactions where each
transaction is denoted by a key and no retry is needed, making this pattern highly desirable in high write workloads.

To block until the send function has completed set the :wait-response to true when calling the start-channel function.

### send 

The send function has can be called either with ```(send :k (fn [n] n) 1)``` this will use the default wait-response parameter,
or with ```(send false :k (fn [n] n) 1)``` and this will override the wait-response parameter.

### removing keys

If the keys grow with time the star channel's internal map will grow, its important to remove keys when they are no longer needed.
To remove a key do ```(send :mykey :remove nil)```

You can also send a function and then in the same call remove the channel. To do this write ```(send :mykey [:remove inc] 1)```

### Example

```clojure
(use 'fun-utils.core)
(import 'java.util.concurrent.Executors)
(import 'java.util.concurrent.TimeUnit)
(import 'java.io.File)

 (let [base-dir (doto (File. "target/tests/star-channel-tests/concurrent") (.mkdirs))
       {:keys [send close]} (star-channel :wait-response true) ;if we set wait-response false the numbers written to the file will be out of order, 
							       ;it does provide more concurrency, and if the ordering is not important use :wait-response false.
       file-a (doto (File. base-dir "file-a") (.delete) (.createNewFile))
       file-b (doto (File. base-dir "file-b") (.delete) (.createNewFile))
       exec (Executors/newCachedThreadPool)]
                   
       (dotimes [i 100]
              (submit exec #(send :a (fn [f] (spit f (str i "\n") :append true)) file-a) ))
                   
       (dotimes [i 100]
              (submit exec #(send :b (fn [f] (spit f (str i "\n") :append true)) file-b) ))
          
       ;wait for threads
       (doto exec (.shutdown) (.awaitTermination 10 TimeUnit/SECONDS))
                       
       (prn (->> file-a (clojure.java.io/reader) (line-seq) (map #(Long/parseLong %)) sort)
       
                           ))
;(0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48 49 50 
; 51 52 53 54 55 56 57 58 59 60 61 62 63 64 65 66 67 68 69 70 71 72 73 74 75 76 77 78 79 80 81 82 83 84 85 86 87 88 89 90 91 92 93 94 95 96 97 98 99)


```

## Buffering and timeouts

There are many times where you need to buffer up a series off results and then perform an operation on them, and if the count is not reached
on a predefined timeout do the operation with the results collected.

```clojure

(let [ch-source (chan)
      buff-ch (buffered-chan ch-source 10 1000 11)]
               
    (go
       (dotimes [i 100]
               (>! ch-source i)))
               
               
    (dotimes [i 10]
            (let [v (<!! buff-ch)]
                   (prn "got " v))))
               
;; "got " [0 1 2 3 4 5 6 7 8 9]
;; "got " [10 11 12 13 14 15 16 17 18 19]
;; "got " [20 21 22 23 24 25 26 27 28 29]
;; "got " [30 31 32 33 34 35 36 37 38 39]
;; "got " [40 41 42 43 44 45 46 47 48 49]
;; "got " [50 51 52 53 54 55 56 57 58 59]
;; "got " [60 61 62 63 64 65 66 67 68 69]
;; "got " [70 71 72 73 74 75 76 77 78 79]
;; "got " [80 81 82 83 84 85 86 87 88 89]
;; "got " [90 91 92 93 94 95 96 97 98 99]

;; the buffered-chan excepts a check function as last argument [ch-source buffer-count timeout-ms buffer-or-n check-f] 
;; that allows for custom checking apart from the count and timeout
;; please see fun-utils.buffered-chan-tests for more information

```


## License

ECLIPSE PUBLIC LICENSE
