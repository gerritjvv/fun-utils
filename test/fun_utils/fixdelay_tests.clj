(ns fun-utils.fixdelay-tests
  (:require [fun-utils.core :refer [fixdelay]])
  (:import [java.util.concurrent.atomic AtomicInteger])
  (:use midje.sweet))

(facts "Test fixdelay"
       
       (fact "Test fix delay runs"
             (let [counter (AtomicInteger.)]
               (fixdelay 100 (.incrementAndGet counter))
               (Thread/sleep 500)
               (> (.get counter) 2) => true))) 
