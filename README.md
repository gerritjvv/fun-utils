# fun-utils

Clojure utility functions that come up time and again while developing clojure software.


## Usage

Best way to see how to use them is going through the unit tests.
I've tried to test each utlity function in its own test file.

```
[fun-utils "0.1.0"]
```

## fixdelay

Use's clojure.core.async timeout to run an expression every n milliseconds.

Example

```clojure

(fixdelay 1000 (prn "hi"))
;; => Hi ...  every second
 
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

The best way to handle concurrency is with no blocking and sending off messages to queues, but there are some cases where you want to wait for a value 
to come back .e.g reading from a file, and the architecture is simpler or does not allow for sending off a method that eventually will get called.

There is also a need to only block on a certain key, i.e. lets say we have a map of files that we write to, there are two points on concurrency concern,
one is the modification of the map, and the other is the writing to the files.

To solve this we can use a single master channel to create a channel per file, add it to a map, and then write to the file and notify a response via a temporary channel.
It happens also that this pattern can be abstracted to work with connections, dbs and any IO, it also provides implicit serialization of transactions where each
transaction is denoted by a key and no retry is needed, making this pattern highly desirable in high write workloads.

Example

```clojure
(use 'fun-utils.core)
(import 'java.util.concurrent.Executors)
(import 'java.util.concurrent.TimeUnit)
(import 'java.io.File)

 (let [base-dir (doto (File. "target/tests/star-channel-tests/concurrent") (.mkdirs))
       {:keys [send close]} (star-channel)
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

## License

ECLIPSE PUBLIC LICENSE