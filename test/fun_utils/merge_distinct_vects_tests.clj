(ns fun-utils.merge-distinct-vects-tests
  (:require [fun-utils.core :refer [merge-distinct-vects]])
  (:use midje.sweet))

(facts "Test merging distinct vects"
       
       (fact "Test merge-distinct-vects"
             (sort (merge-distinct-vects [1 2 5 6 3] [1 2 8 9])) => [1 2 3 5 6 8 9]
             ))
