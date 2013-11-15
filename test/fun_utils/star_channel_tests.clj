(ns fun-utils.star-channel-tests
  (:require [fun-utils.core :refer [star-channel submit]])
  (:import [java.io File]
           [java.util.concurrent Executors TimeUnit])
  (:use midje.sweet))


(facts "Test star channel"
      (comment
       (fact "Check that different keys are created"
             ;star-channel [& {:keys [master-buff buff] :or {master-buff 100 buff 100}}]
             (let [{:keys [send close]} (star-channel)]
               (send :a inc 1) => 2
               (send :a inc 2) => 3
               (send :b inc 1) => 2
               
               )))
       (fact "Check concurrency"
             ;star-channel [& {:keys [master-buff buff] :or {master-buff 100 buff 100}}]
             (let [
                   base-dir (doto (File. "target/tests/star-channel-tests/concurrent")
                                  (.mkdirs))
                   {:keys [send close]} (star-channel)
                   file-a (doto (File. base-dir "file-a") (.delete) (.createNewFile))
                   file-b (doto (File. base-dir "file-b") (.delete) (.createNewFile))
                   exec (Executors/newCachedThreadPool)]
                   
                   (dotimes [i 100]
                           (submit exec #(send :a (fn [f] (spit f (str i "\n") :append true)) file-a) ))
                   
                   (dotimes [i 100]
                           (submit exec #(send :b (fn [f] (spit f (str i "\n") :append true)) file-b) ))
                   
                   ;wait for threads
                   (doto exec
                     (.shutdown)
                     (.awaitTermination 10 TimeUnit/SECONDS))
                   
                   ;check file contents
                   (with-open [rdr (clojure.java.io/reader file-a)]
                     (loop [i 0 lines (sort (map #(Long/parseLong %) (line-seq rdr)))]
                       (if-let [line (first lines)]
                          (do 
                            line => (long i)
                            (recur (inc i) (rest lines))))))
                   
                   (with-open [rdr (clojure.java.io/reader file-b)]
                     (loop [i 0 lines (sort (map #(Long/parseLong %) (line-seq rdr)))]
                       (if-let [line (first lines)]
                          (do 
                            line => (long i)
                            (recur (inc i) (rest lines))))))
                   
                   )))
                   
             
