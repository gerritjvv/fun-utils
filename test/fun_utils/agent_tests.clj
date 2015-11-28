(ns fun-utils.agent-tests
  (:require [fun-utils.agent :as agnt])
  (:use midje.sweet))


(fact "Test blocking agent no blocking"
      (let [a-map (agnt/agent {})]
        (dotimes [i 1000] (agnt/send a-map assoc-in [:a :b] 1))
        (Thread/sleep 1000)
        @a-map => {:a {:b 1}}))


(fact "Test blocking agent blocking"
      (let [a-map (agnt/agent {})
            blocking (fn [& _] (Thread/sleep 2000))]
        (try
          (do
            (agnt/send a-map blocking) => truthy

            (agnt/send a-map blocking) => truthy

            (agnt/send a-map blocking :timeout 100) => truthy)
          (finally
            (agnt/close-agent a-map)))))
