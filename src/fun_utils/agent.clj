(ns
  ^{:doc "Improvements on clojure's agent(s) by allowing for blocking agents to avoid OOM when having slow processing functions"}
  fun-utils.agent
  (:require [fun-utils.threads :as threads]
            [clojure.tools.logging :refer [error]]
            [fun-utils.core :as core]

            [clojure.core.async :as async])
  (:refer-clojure :exclude [agent send-via send-off send])
  (:import
    (clojure.lang IDeref)
    (java.util.concurrent.atomic AtomicReference AtomicBoolean)
    (java.util.concurrent CountDownLatch)))


(deftype BlockingAgent [chan ^AtomicReference state-ref ^AtomicBoolean closed ^CountDownLatch close-complete]
  IDeref
  (deref [_] (.get state-ref)))

(defn closed? [^BlockingAgent agnt]
  (.get ^AtomicBoolean (.closed agnt)))

(defn log-error
  "Prints and error using clojure.tools.logging/error"
  [e f]
  (error (str "Error while running in agent f: " f " error " e) e))

(defn agent
  "Create a blocking agent using async channels with length of mailbox-len default 10.
   This agent must be closed after usage using the close-agent function.
   The agent implements IDeref and can be read using @agent"
  ^{:arg-lists '([mailbox-len number? error-handler (fn [error function-sent])])}
  [state & {:keys [mailbox-len error-handler] :or {mailbox-len 10 error-handler log-error}}]
  (let [ch (async/chan mailbox-len)
        state-ref (AtomicReference. state)
        ^AtomicBoolean closed (AtomicBoolean. false)
        ^CountDownLatch close-complete (CountDownLatch. 1)
        ]
    ;functions are send to the channel ch which are called as
    ; state = (f state)
    (core/thread-seq2
      (fn [f]
        (try
          (.set state-ref (f (.get state-ref)))
          (catch Exception e (error-handler e e))))
      (fn []
        (.countDown close-complete)) ch)

    (BlockingAgent. ch state-ref closed close-complete)))

(defn excepton-if-timeout [val]
  (when (= val :timeout)
    (throw (RuntimeException. (str "Timeout while waiting to send to agent")))))

(defn send-timeout [^BlockingAgent a f timeout & args]
  (if (closed? a)
    (throw (RuntimeException. "Cannot write to a closed agent"))
    (excepton-if-timeout (async/alt!! (async/timeout timeout) :timeout
                                      [[(.chan a) (fn [v] (apply f v args))]] :sent)))
  a)

(defn send
  "Send a function f to the agent to be executed asynchronously but in serial with other functions
   to the agent using (apply f value-of-agent args), if the mailbox is full this function blocks"
  [^BlockingAgent a f & args]
  (if (closed? a)
    (throw (RuntimeException. "Cannot write to a closed agent"))
    (async/>!! (.chan a) (fn [v] (apply f v args))))
  a)

(defn close-agent
  "Closes the mailbox for the agent
   if wait is true the agent will close and wait for the last function to process"
  [^BlockingAgent a & {:keys [wait] :or {wait false}}]
  (.set ^AtomicBoolean (.closed a) true)
  (async/close! (.chan a))
  (when wait
    (.await ^CountDownLatch (.close-complete a))))

