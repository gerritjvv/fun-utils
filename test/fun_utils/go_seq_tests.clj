(ns fun-utils.go-seq-tests
  (:require [fun-utils.core :refer [go-seq]]
            [clojure.core.async :refer [chan >!! <!! close!]])
  (:use midje.sweet)
  (:import [java.util.concurrent.atomic AtomicReference]))

(facts "Test go-seq"
  
  
  (fact "Test go seq f ch"
    
    (let [ch (chan 10)
          reg (AtomicReference. false)]
      
      (go-seq (fn [v] (.set reg v)) ch)
      
      (>!! ch 1) (Thread/sleep 200)
      (.get reg) => 1
      (>!! ch 2) (Thread/sleep 200)
      (.get reg) => 2
      
      (close! ch)
      (>!! ch 3) (Thread/sleep 200)
      (.get reg) => 2 ;the loop has terminated, the value will not be set
      
    
    ))
    (fact "Test go seq f ch & chs"
    
    (let [ch1 (chan 10)
          ch2 (chan 10)
          ch3 (chan 10)
          
          reg (AtomicReference. false)]
      
      (go-seq (fn [v ch] 
                (.set reg [v ch])
                (not (= ch3 ch))) ch1 ch2 ch3)
      
      (>!! ch1 1) (Thread/sleep 200)
      (.get reg) => [1 ch1]
      (>!! ch2 2) (Thread/sleep 200)
      (.get reg) => [2 ch2]
      (>!! ch3 3) (Thread/sleep 200)
      (.get reg) => [3 ch3]
      
      
      (>!! ch1 4) (Thread/sleep 200)
      (.get reg) => [3 ch3]  ;the loop has terminated, the value will not be set
      
    
    )))

