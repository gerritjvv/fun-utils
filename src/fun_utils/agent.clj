(ns
  ^{:doc "Improvements on clojure's agent(s) by allowing for blocking agents to avoid OOM when having slow processing functions"}
  fun-utils.agent
  (:require [fun-utils.threads :as threads]
            [fun-utils.core :as core]

            [clojure.core.async :as async])
  (:refer-clojure :exclude [agent send-via send-off send])
  (:import
    (clojure.lang IDeref)
    (java.util.concurrent.atomic AtomicReference)))


(deftype BlockingAgent [chan ^AtomicReference state-ref]
  IDeref
  (deref [_] (.get state-ref)))

(defn agent
  "Create a blocking agent using async channels with length of mailbox-len default 10.
   This agent must be closed after usage using the close-agent function.
   The agent implements IDeref and can be read using @agent"
  [state & {:keys [mailbox-len] :or {mailbox-len 10}}]
  (let [ch (async/chan mailbox-len)
        state-ref (AtomicReference. state)]
    ;functions are send to the channel ch which are called as
    ; state = (f state)
    (core/thread-seq
      (fn [f]
        (.set state-ref (f (.get state-ref)))) ch)

    (BlockingAgent. ch state-ref)))

(defn send
  "Send a function f to the agent to be executed asynchronously but in serial with other functions
   to the agent using (apply f value-of-agent args), if the mailbox is full this function blocks"
  [^BlockingAgent a f & args]
  (async/>!! (.chan a) (fn [v] (apply f v args)))
  a)

(defn close-agent
  "Closes the mailbox for the agent"
  [^BlockingAgent a]
  (async/close! (.chan a)))

