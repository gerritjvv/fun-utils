(ns fun-utils.cache-tests
  (:require [fun-utils.cache :as c])
  (:use midje.sweet))


(facts "Test cache implementations"

       (fact "Test Loader cache"
             (let [cache (c/create-cache)]
               (get cache 1) => nil
               (get cache 1 2) => 2
               (assoc cache 1 :a)
               (get cache 1) => :a
               (keys cache) => '(1)
               (vals cache) => '(:a)
               (dissoc cache 1)
               (get cache 1) => nil))

       (fact "Test Loading cache"
             (let [cache (c/create-loading-cache (fn [k] [k 1]))]
              (get cache 1) => [1 1]
              (get cache 2) => [2 1]))

       (fact "Test Expire cache"
             (let [cache (c/create-cache :expire-after-write 400)]
               (get cache 1) => nil
               (assoc cache 1 :a)
               (get cache 1) => :a
               (Thread/sleep 1500)
               (get cache 1) => nil)))
