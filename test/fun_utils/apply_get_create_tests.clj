(ns fun-utils.apply-get-create-tests
  (:require [fun-utils.core :refer [apply-get-create]])
  (:use midje.sweet))


(facts "Test apply-get-create"
       
       (fact "test new value created"
             (apply-get-create {} :a inc (fn [& args] 1) ) => {:a 1}
             
             ;the inc is applied but the result is not assoced to the map
             ;this method is used to creawte resources in a map if they do not exist
             ;e.g. agents, channels, files
             (apply-get-create {:a 1} :a inc (fn [& args] 1) ) => {:a 1}
             )
       (fact "test with ref"
            (let [v (ref {})]
             (dosync
               (alter v apply-get-create :a (fn [agnt] (send agnt inc)) (fn [& args] (agent 1)))
               (alter v apply-get-create :a (fn [agnt] (send agnt inc)) (fn [& args] (agent 1)))
               (alter v apply-get-create :a (fn [agnt] (send agnt inc)) (fn [& args] (agent 1))) )
             
              (prn @v)
              (deref (:a @v)) => 4
              
              
             ))
       
       )
