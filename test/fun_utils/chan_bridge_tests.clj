(ns fun-utils.chan-bridge-tests
  (:require [fun-utils.core :refer [chan-bridge]]
            [clojure.core.async :refer [chan >!! <!!]])
  (:use midje.sweet))


(facts "Test copying from one channel to another"

       (fact "simple bridge"
             (let [ch1 (chan 10)
                   ch2 (chan-bridge ch1 (chan 10))]


               (doseq [i (range 5)] (>!! ch1 i))

               (reduce + (repeatedly 5 #(<!! ch2))) => 10))

       (fact "mapped bridge"
             (let [ch1 (chan 10)
                   ch2 (chan-bridge ch1 inc (chan 10))]

               (doseq [i (range 5)] (>!! ch1 i))

               ;(prn (repeatedly 5 #(<!! ch2)))
               (reduce + (repeatedly 5 #(<!! ch2))) => 15

               )))
       