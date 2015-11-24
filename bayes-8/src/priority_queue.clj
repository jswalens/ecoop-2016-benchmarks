(ns priority-queue
  (:refer-clojure :exclude [remove]))

(defn create []
  "Create an empty priority queue. Its elements will be ordered based on their
  value."
  (list))

(defn add [queue val]
  "Add `val` to `queue`."
  (sort (conj queue val)))

(defn remove [queue val]
  "Remove `val` from `queue`."
  (clojure.core/remove #(= % val) queue))
