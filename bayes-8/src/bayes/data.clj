(ns bayes.data
  (:refer-clojure :exclude [sort])
  (:require [clojure.math.numeric-tower :as math]
            [bitmap]
            [random]
            [bayes.net :as net])
  (:import [java.util LinkedList]))

(def ^:const DATA_PRECISION 100)

;
; Helper functions to access records
;

(defn- get-record [data id]
  "Get record with index `id` from `data`."
  (nth (:records data) id))

(defn- get-var [record offset]
  "Get column `offset` in `record`."
  (nth record offset))

(defn- get-record-var [data id offset]
  "Get column `offset` in record with index `id` from `data`."
  (get-var (get-record data id) offset))

;
; generate
;

(defn- bits->bitmap [bits]
  "Convert a list of 0 and 1 to a bitmap, i.e. an int where each bit is set
  correspondingly.
  (bits->bitmap (list 1 0 1)) => 0b101"
  (reduce #(+ (* %1 2) %2) 0 bits))

(defn- concat-uniq [xs ys]
  "Concat `xs` and `ys`, but do not add elements in `ys` that are already in
  `xs`."
  (if (empty? xs)
    ys
    (concat xs (filter #(not (.contains xs %)) ys))))

(defn generate [params]
  "Allocate and generate data, returns `{:data data :net net}`.

  This replaces the C version's alloc and generate functions.

  As opposed to the C version, this doesn't take a seed."
  (let [n-var          (:var params)
        n-record       (:record params)
        max-num-parent (:number params)
        percent-parent (:percent params)
        ; Generate random Bayesian network
        net
          (net/generate-random-edges
            (net/alloc n-var) max-num-parent percent-parent)
        ; Create variable dependency ordering for record generation.
        ; Each of order[i]'s parents are sorted before i in order, i.e:
        ;   for all i: for all p in parents[order[i]]:
        ;   index-of(p in order) < i   [1]
        order
          (loop [id    0  ; id of node currently being visited (index)
                 order [] ; order of node ids (accumulator)
                 done  (bitmap/create n-var)] ; nodes that have been visited
            (if (nil? id)
              order ; bitmap/find-clear found no more nodes => everything's done
              (if (not= (count (net/child-ids net id)) 0)
                ; This node has children
                (recur (bitmap/find-clear done (inc id)) order done)
                ; This node has no children, it is a leaf
                (let [; Use breadth-first search to find net connected to this
                      ; leaf (i.e. ancestors of this leaf)
                      queue (LinkedList.)
                      _     (.add queue id)
                      dependencies
                        (loop [dependencies (list)]
                          (if (empty? queue)
                            dependencies
                            (let [current (.pop queue)]
                              (.addAll queue (net/parent-ids net current))
                              (recur (cons current dependencies)))))
                      done-1
                        (reduce #(bitmap/set %1 %2) done dependencies)
                      order-1
                        (concat-uniq order dependencies)]
                  (recur (bitmap/find-clear done (inc id)) order-1 done-1)))))
        ; Create a threshold for each of the possible permutations of variable
        ; value instances.
        ; This is a 2D array: variable -> bitmap -> (random) int. The bitmap has
        ; length = the variable's number of parents. All permutations of the
        ; bitmap are iterated through. So, given a variable and an on/off state
        ; for each of its parents, this returns a (randomly generated) integer.
        thresholds
          (for [v (range n-var)]
            (for [t (range (math/expt 2 (count (net/parent-ids net v))))]
              (random/rand-int (inc DATA_PRECISION))))
        ; Create records
        ; records is a list mapping each record id to a record, which is a list
        ; of 0s and 1s of length n-var
        records
          (for [r (range n-record)]
            (reduce
              (fn [record o]
                (let [id        (nth order o)
                      values    ; list of 0s and 1s
                                (for [p (net/parent-ids net id)]
                                  ; ordering ensures that p < o (see [1]), so
                                  ; record will have index p at iteration o of
                                  ; the reduce
                                  (get-var record p))
                      bitmap    (bits->bitmap values)
                      threshold (nth (nth thresholds id) bitmap)
                      rnd       (random/rand-int DATA_PRECISION)]
                  (if (< rnd threshold)
                    (assoc record id 1)
                    (assoc record id 0))))
              (vec (repeat n-var 0))
              (range n-var)))]
    ; Return
    {:data {:n-var n-var :n-record n-record :records records} :net net}))

;
; sort
;

(defn- compare-record [a b offset]
  "Compare records `a` and `b` by a lexicographic order on its columns starting
  at `offset`.
  Assumes `a` and `b` are the same size."
  (let [c (compare (get-var a offset) (get-var b offset))]
    (if (= c 0)
      (if (>= (inc offset) (count a))
        0
        (compare-record a b (inc offset)))
      c)))

(defn- sort-records [records start n offset]
  "Sort records with id `start` to `start + n` in `records`, based on values in
  their columns at index `offset` and later. Returns sorted records (embedded
  in all records)."
  (let [p1 (take start records)          ; 0 -> start-1
        p2 (take n (drop start records)) ; start -> start+n-1
        p3 (drop (+ start n) records)]   ; start+n -> end
    (concat
      p1
      (clojure.core/sort #(compare-record %1 %2 offset) p2)
      p3)))

(defn sort [data start n offset]
  "Sort records with id `start` to `start + n` in `data`, based on values in
  their columns at index `offset` and later. Returns updated data."
  (assoc data :records (sort-records (:records data) start n offset)))

;
; find-split
;

(defn find-split [data start n offset]
  "We take the records with id `start` to `start + n` from `data`. Then, we take
  their column at index `offset`. The first `x` of these values should be 0,
  the next `n - x` should be 1. [*] This function returns that `x`.

  [*] To satisfy this condition, run data/sort on the data first.

  This function uses binary search."
  (loop [low  start
         high (+ start n -1)]
    (if (> low high)
      (- low start)
      (let [mid (int (/ (+ low high) 2))]
        (if (= (get-record-var data mid offset) 0)
          (recur (inc mid) high)
          (recur low (dec mid)))))))
