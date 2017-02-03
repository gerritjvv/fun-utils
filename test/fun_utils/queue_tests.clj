(ns fun-utils.queue-tests
  (:require [fun-utils.queue :as queue])
  (:use midje.sweet))

(fact "Test Different Queue implementations"

      (defn test-queue [t]
        (let [q (queue/queue-factory t 2)
              q2 (queue/queue-factory t 100)]
          (queue/offer! q 10) => true
          (queue/offer! q 10) => true
          (queue/offer! q 10 500) => falsey
          (queue/size q) => 2

          (queue/poll! q) => 10
          (queue/poll! q 1000) => 10
          (queue/poll! q 500) => falsey

          (dotimes [i 10]
            (queue/offer! q2 i))

          (queue/-drain! q2 3) => (some-checker #(= % [0 1 2]) #(= % [0]))))

      (test-queue :array-queue)
      (test-queue :spmc-array-queue)
      (test-queue :mpmc-array-queue))