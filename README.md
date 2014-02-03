# fun-utils

Clojure utility functions that come up time and again while developing clojure software.


## Usage

Best way to see how to use them is going through the unit tests.
I've tried to test each utlity function in its own test file.

```
[fun-utils "0.3.6"]
```

## fixdelay

Use's clojure.core.async timeout to run an expression every n milliseconds.

Example

```clojure

(def d (fixdelay 1000 (prn "hi")))

;; => Hi ...  every second

(stop-fixdelay d)
;;stops the fixdelay

```

## go-seq

When writing go blocks there is a repeating pattern to avoid 100% cpu spin when the channel is closed.

e.g.
if we run the below code, the loop will spin without pause.

```
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

```

(def ch (chan 10))
(go-loop []
 (if-let [v (<! ch)]
   (do (prn v) (recur))))
   
(close! ch)
```

To make this pattern easier we can use go-seq like so:

```
(def ch (chan 10))

(go-seq #(prn v) ch)
;; go-seq will call the function every time a none nil value is seen on ch
;; if a nil value is seen the loop is terminated

(close! ch)

```

For multiple channels, the function is called as (f v ch) and if f returns false the loop is terminated.

```
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

```

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
          
```


## License

ECLIPSE PUBLIC LICENSE
