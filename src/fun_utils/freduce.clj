(ns fun-utils.freduce)


;; unroll reduce sequences
(comment

  (def l (vec (range 0 1000)))

  (defn flatten-one-level [coll]
    (mapcat #(if (sequential? %) % [%]) coll))

  (defmacro freduce [f init size c]
    (let [c1 (gensym)]
      `(let [~c1 ~c]
         ~(flatten-one-level
            `(->
               (~f ~init (~nth ~c1 0))
               ~(for [i (range 1 size)]
                  `(~f (~nth ~c1 ~i))))))))
  )
(comment

  (->
    (+ init (nth c 0))
    (+ (nth c 1))
    (+ (nth c 2))))
