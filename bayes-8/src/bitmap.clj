(ns bitmap
  (:refer-clojure :exclude [set]))

(defn create [length]
  "Create a bitmap of `length` falses."
  (vec (repeat length false)))

(defn set [bitmap i]
  "Set bit `i` in `bitmap` to true."
  (assoc bitmap i true))

(defn is-set? [bitmap i]
  "Returns true if bit `i` in `bitmap` is true."
  (= (nth bitmap i) true))

(defn find-clear [bitmap start-index]
  "Returns the index of the first false element in `bitmap`, after
  `start-index`. Returns nil if all bits are true."
  (loop [i start-index]
    (if (< i (count bitmap))
      (if (is-set? bitmap i)
        (recur (inc i))
        i)
      nil)))
