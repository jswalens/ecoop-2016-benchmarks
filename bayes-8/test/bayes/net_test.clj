(ns bayes.net-test
  (:require [clojure.test :refer :all]
            [bayes.net :as net]))

(def linear-net
  "Net of three nodes with edges 0 -> 1 -> 2."
  (dosync
    (-> (net/alloc 3)
      (net/insert-edge 0 1)
      (net/insert-edge 1 2))))

(def central-net
  "Net of three nodes with edges 0 -> 1 <- 2."
  (dosync
    (-> (net/alloc 3)
      (net/insert-edge 0 1)
      (net/insert-edge 2 1))))

(defn- deref-net [net]
  (map deref net))

(defn- copy-net [net]
  (dosync
    (map ref (map deref net))))

(deftest alloc-insert
  (is (=
    [{:id 0 :parent-ids []   :child-ids '(1)} ; 0 -> 1
     {:id 1 :parent-ids '(0) :child-ids '(2)} ; 0 -> 1 -> 2
     {:id 2 :parent-ids '(1) :child-ids []}]  ; 1 -> 2
    (deref-net linear-net)))
  (is (=
    [{:id 0 :parent-ids []     :child-ids '(1)}  ; 0 -> 1
     {:id 1 :parent-ids '(0 2) :child-ids []}    ; (0 2) -> 1, order is important!
     {:id 2 :parent-ids []     :child-ids '(1)}] ; 2 -> 1
    (deref-net central-net))))

(deftest remove-edge
  (is (=
    [{:id 0 :parent-ids []   :child-ids '(1)} ; 0 -> 1
     {:id 1 :parent-ids '(0) :child-ids []}   ; 0 -> 1
     {:id 2 :parent-ids []   :child-ids []}]
    (deref-net (net/remove-edge (copy-net linear-net) 1 2))))
  ; remove non-existing edge
  #_(is (= linear-net (net/remove-edge (copy-net linear-net) 0 2))))

(deftest reverse-edge
  (is (= (deref-net central-net)
         (deref-net (net/reverse-edge (copy-net linear-net)  1 2))))
  (is (= (deref-net linear-net)
         (deref-net (net/reverse-edge (copy-net central-net) 2 1)))))

(deftest has-edge?
  (is (net/has-edge? linear-net 0 1))
  (is (net/has-edge? linear-net 1 2))
  (is (not (net/has-edge? linear-net 0 2))))

(deftest has-path?
  (is (net/has-path? linear-net 0 1))
  (is (net/has-path? linear-net 1 2))
  (is (net/has-path? linear-net 0 2))
  (is (not (net/has-path? linear-net 2 0)))
  (is (not (net/has-path? central-net 0 2))))

(deftest has-cycle?
  (is (not (net/has-cycle? linear-net)))
  (is (net/has-cycle? (-> linear-net (copy-net) (net/insert-edge 2 0))))
  (is (net/has-cycle? (-> linear-net (copy-net) (net/insert-edge 1 0))))
  (is (not (net/has-cycle? central-net)))
  (is (net/has-cycle? (-> central-net (copy-net) (net/insert-edge 1 0))))
  (is (not (net/has-cycle? (-> central-net (copy-net) (net/insert-edge 0 2))))))

(deftest concat-uniq
  (are [xs ys expected] (= expected (@#'bayes.net/concat-uniq xs ys))
    [1 2 3] [4 5 6] [1 2 3 4 5 6]
    [1 2 3] [1 5 6] [1 2 3 5 6]
    [1 2 3] [1 3 3] [1 2 3]
    []      [4 5 6] [4 5 6]
    [1 2 3] []      [1 2 3]))

(deftest find-descendants
  (is (= #{1 2} (net/find-descendants linear-net 0)))
  (is (= #{2}   (net/find-descendants linear-net 1)))
  (is (= #{}    (net/find-descendants linear-net 2)))
  (is (= #{1}   (net/find-descendants central-net 0)))
  (is (= #{}    (net/find-descendants central-net 1)))
  (is (= #{1}   (net/find-descendants central-net 2))))
