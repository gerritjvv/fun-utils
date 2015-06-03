(ns
  ^{:doc "Performant functions unrolling get-in and assoc-in functions
          copied from http://blog.podsnap.com/tinhole.html"}
  fun-utils.assocs
  (:refer-clojure :exclude [get-in assoc-in]))


(defmacro get-in
  "A macro for doing get in when the keys are known at compile time"
  [m path]
  (reduce (fn [acc k] (concat acc (list (if (vector? k)
                                          `(~(second k))
                                          `(get ~k)))))
          `(-> ~m) path))

(defn- th-assoc-in-gen [m ks v]
  (let [k  (first ks)
        ks (next ks)]
    (cond
      (vector? k) (let [[f-in f-out] k]
                    (list f-in
                          (if-not ks v (th-assoc-in-gen (list f-out m) ks v))))
      ks        (list 'assoc m k (th-assoc-in-gen (list 'get m k) ks v ))
      :else     (list 'assoc m k v))))

(defmacro assoc-in
  "A macro for assoc-in when the keys are known at compile time"
  [m ks v] (th-assoc-in-gen m ks v))