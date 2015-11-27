(ns fun-utils.cache
  (:import [com.google.common.cache CacheBuilder CacheLoader Cache LoadingCache]
           [com.google.common.util.concurrent SettableFuture]
           [java.util.concurrent TimeUnit Callable])
  (:require [potemkin.collections :refer [def-map-type]])
  (:refer-clojure :exclude [memoize]))

;; Uses guava cache

(defn ^CacheLoader cache-loader [f]
  (proxy [CacheLoader] []
    (load [k] (f k))
    (reload [k oldvalue]
            (let [fut (SettableFuture/create)]
              (future (.set fut (f k)))
              fut))))


(def-map-type GuavaLoadingCacheMap [^LoadingCache cache mta]
              (get [_ k default-value]
                   (if-let [v (.get cache k)] v default-value))
              (assoc [this k v]
                (.put cache k v)
                this)
              (dissoc [this k]
                      (.invalidate cache k)
                      this)
              (keys [_]
                    (-> cache .asMap .keySet))
              (meta [_] mta)
              (with-meta [_ mta]
                         (GuavaLoadingCacheMap. cache mta)))

(def-map-type GuavaCacheMap [^Cache cache mta]
              (get [_ k default-value]
                   (if-let [v (.getIfPresent cache k)] v default-value))
              (assoc [this k v]
                (.put cache k v)
                this)
              (dissoc [this k]
                      (.invalidate cache k)
                      this)
              (keys [_]
                    (-> cache .asMap .keySet))
              (meta [_] mta)
              (with-meta [_ mta]
                         (GuavaCacheMap. cache mta)))

(defn ^CacheBuilder -configure-cache
  [& {:keys [concurrency-level
             expire-after-access
             expire-after-write
             refresh-after-write
             soft-values
             weak-keys
             weak-values
             maximum-size
             time-unit] :or {^TimeUnit time-unit TimeUnit/MILLISECONDS}}]
  (let [^CacheBuilder builder (CacheBuilder/newBuilder)]
    (if maximum-size
      (.maximumSize builder (long maximum-size)))
    (if concurrency-level
      (.concurrencyLevel builder (int concurrency-level)))
    (if expire-after-access
      (.expireAfterAccess builder (long expire-after-access) time-unit))
    (if expire-after-write
      (.expireAfterWrite builder (long expire-after-write) time-unit))
    (if refresh-after-write
      (.refreshAfterWrite builder (long refresh-after-write) time-unit))
    (if soft-values
      (.softValues builder))
    (if weak-keys
      (.weakKeys builder))
    (if weak-values
      (.weakValues builder))
    builder))


(defn -create-loading-cache [loader-f & args]
  {:pre [(fn? loader-f)]}
  (.build ^CacheBuilder(apply -configure-cache args) (cache-loader loader-f)))

(defn -create-cache [& args]
  (.build ^CacheBuilder(apply -configure-cache args)))

(defn create-loading-cache [loader-f & args]
  (:pre [(fn? loader-f)])
  (GuavaLoadingCacheMap. (apply -create-loading-cache loader-f args) {}))

(defn create-cache
  [& args]
  (GuavaCacheMap. (apply -create-cache args) {}))


(defn memoize-1
  "Fast memoize for single arity functions backed by guava cache"
  [f & args]
  (let [c (apply create-loading-cache f args)]
    (fn [k]
      (get c k))))

(defn memoize
  "Memoize backed by guava cache"
  [f & args]
  (let [c (apply create-cache args)]
    (fn [k & args]
      (if-let [v (get c k)]
        v
        (when-let [v (apply f k args)]
          (assoc c k v)
          v)))))


