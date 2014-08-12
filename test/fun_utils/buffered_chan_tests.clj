(ns fun-utils.buffered-chan-tests
  (:require [fun-utils.core :refer [buffered-chan]]
            [clojure.core.async :refer [>!! <!! chan >! <! go]])
  (:use midje.sweet))


(facts "Test buffered-chan"

       (fact "Test reading buffer"
             (let [ch-source (chan)
                   buff-ch (buffered-chan ch-source 10 50000 11)]

               (go
                 (dotimes [i 100]
                   (>! ch-source i)))


               (dotimes [i 10]
                 (let [v (<!! buff-ch)]
                   v => (map (fn [x] (+ (* i 10) x)) (range 10))))

               ))
       (fact "Test timeout"
             (let [ch-source (chan)
                   buff-ch (buffered-chan ch-source 10 500)]
               (>!! ch-source 1)
               (Thread/sleep 600)
               (<!! buff-ch) => [1]

               ))

       (fact "Test using custom function"
             (let [ch-source (chan)
                   buff-ch (buffered-chan ch-source 10 10000 10 (fn
                                                                  ([] [false nil])
                                                                  ([acc v] [(= v [1 2]) nil])))]
               (>!! ch-source 1)
               (>!! ch-source 2)

               (Thread/sleep 200)
               (<!! buff-ch) => [1 2]

               ))
       )
               
             
