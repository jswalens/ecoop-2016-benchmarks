(ns bayes.learner-test
  (:require [clojure.test :refer :all]
            [bayes.adtree :as adtree]
            [bayes.net :as net]
            [bayes.learner :as learner]))

(defn almost= [a b]
  "Is the difference between `a` and `b` smaller than 0.00001?"
  (< (Math/abs (- a b)) 0.00001))

(deftest add-task
  (are [tasks task expected] (= expected (@#'bayes.learner/add-task tasks task))
    (ref [{:op 3 :score 3} {:op 1 :score 1}]) {:op 5 :score 5}
      [{:op 5 :score 5} {:op 3 :score 3} {:op 1 :score 1}]
    (ref [{:op 3 :score 3} {:op 1 :score 1}]) {:op 2 :score 2}
      [{:op 3 :score 3} {:op 2 :score 2} {:op 1 :score 1}]))

(deftest add-tasks
  (is (=
    [{:op 4 :score 4} {:op 3 :score 3} {:op 2 :score 2} {:op 1 :score 1}]
    (@#'bayes.learner/add-tasks
      (ref [{:op 3 :score 3} {:op 1 :score 1}])
      [{:op 4 :score 4} {:op 2 :score 2}]))))

(deftest pop-task
  (let [tasks (ref [{:op 3 :score 3} {:op 2 :score 2} {:op 1 :score 1}])]
    (is (= {:op 3 :score 3} (@#'bayes.learner/pop-task tasks)))
    (is (= [{:op 2 :score 2} {:op 1 :score 1}] @tasks))))

(deftest set-query-value
  (is (= [{:index 0 :value 10} {:index 1 :value 21} {:index 2 :value 12}]
    (@#'bayes.learner/set-query-value
      [{:index 0 :value 10} {:index 1 :value 11} {:index 2 :value 12}]
      1
      21))))

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

(deftest populate-parent-query-vector
  (are [net id pqv] (= pqv (@#'bayes.learner/populate-parent-query-vector net id))
    linear-net 0  (list)
    linear-net 1  (list 0)
    linear-net 2  (list 1)
    central-net 0 (list)
    central-net 1 (list 0 2)
    central-net 2 (list)))

(deftest populate-query-vectors
  (are [net id pqv qv]
    (= {:query-vector qv :parent-query-vector pqv}
      (@#'bayes.learner/populate-query-vectors net id))
    linear-net 0  (list)     (list 0)
    linear-net 1  (list 0)   (list 0 1)
    linear-net 2  (list 1)   (list 1 2)
    central-net 0 (list)     (list 0)
    central-net 1 (list 0 2) (list 0 1 2)
    central-net 2 (list)     (list 2)))

(def example-data
  {:n-var 3
   :n-record 4
   :records '([1 0 0] [1 0 1] [0 0 0] [0 1 0])})

(def example-net
  [{:id 0 :parent-ids (ref '())  :child-ids (ref '())}
   {:id 1 :parent-ids (ref '(2)) :child-ids (ref '())}
   {:id 2 :parent-ids (ref '())  :child-ids (ref '(1))}])

(def example-adtree
  (adtree/make example-data))

(deftest compute-specific-local-log-likelihood
  ; These values have been verified against the C version.
  (is (almost= -0.346574
    (@#'bayes.learner/compute-specific-local-log-likelihood
      example-adtree
      [{:index 0 :value 0}]
      (list 0)
      (list))))
  (is (almost= -0.346574
    (@#'bayes.learner/compute-specific-local-log-likelihood
      example-adtree
      [{:index 0 :value 1}]
      (list 0)
      (list))))
  (is (almost= -0.173287
    (@#'bayes.learner/compute-specific-local-log-likelihood
      example-adtree
      [{:index 0 :value 0} {:index 1 :value 1} {:index 2 :value 1}]
      (list 0 1)
      (list 0))))
  (is (almost= 0.0
    (@#'bayes.learner/compute-specific-local-log-likelihood
      example-adtree
      [{:index 0 :value 0} {:index 1 :value 1} {:index 2 :value 1}]
      (list 1 2)
      (list 1)))))

(deftest compute-local-log-likelihood
  ; These values have been verified against the C version.
  (is (almost= -0.693147
    (@#'bayes.learner/compute-local-log-likelihood
      0
      example-adtree
      [{:index 0 :value 0} {:index 1 :value 1} {:index 2 :value 1}]
      (list 0)
      (list))))
  (is (almost= -0.346574
    (@#'bayes.learner/compute-local-log-likelihood
      0
      example-adtree
      [{:index 0 :value 0} {:index 1 :value 1} {:index 2 :value 1}]
      (list 0 1)
      (list 0))))
  (is (almost= -0.549306
    (@#'bayes.learner/compute-local-log-likelihood
      0
      example-adtree
      [{:index 0 :value 0} {:index 1 :value 1} {:index 2 :value 1}]
      (list 1 2)
      (list 1))))
  (is (almost= -0.693147
    (@#'bayes.learner/compute-local-log-likelihood
      1
      example-adtree
      [{:index 0 :value 0} {:index 1 :value 1} {:index 2 :value 1}]
      (list 0)
      (list))))
  (is (almost= -0.346574
    (@#'bayes.learner/compute-local-log-likelihood
      1
      example-adtree
      [{:index 0 :value 0} {:index 1 :value 1} {:index 2 :value 1}]
      (list 0 1)
      (list 0))))
  (is (almost= -0.549306
    (@#'bayes.learner/compute-local-log-likelihood
      1
      example-adtree
      [{:index 0 :value 0} {:index 1 :value 1} {:index 2 :value 1}]
      (list 1 2)
      (list 1))))
  (is (almost= -0.693147
    (@#'bayes.learner/compute-local-log-likelihood
      2
      example-adtree
      [{:index 0 :value 0} {:index 1 :value 1} {:index 2 :value 1}]
      (list 0)
      (list))))
  (is (almost= -0.346574
    (@#'bayes.learner/compute-local-log-likelihood
      2
      example-adtree
      [{:index 0 :value 0} {:index 1 :value 1} {:index 2 :value 1}]
      (list 0 1)
      (list 0))))
  (is (almost= -0.477386  ; different than other id's
    (@#'bayes.learner/compute-local-log-likelihood
      2
      example-adtree
      [{:index 0 :value 0} {:index 1 :value 1} {:index 2 :value 1}]
      (list 1 2)
      (list 1)))))

(deftest sum
  (are [xs sum] (= sum (@#'bayes.learner/sum xs))
    [1 2 3]          6
    [0 1 2 7 3 28 3] 44
    []               0))

(def example-params
  {:thread  1
   :edge    1
   :insert  1
   :quality 1.0})

(deftest score
  ; Note: results have been verified against C version.
  ; Simple data: records all "2"
  (let [data    {:n-var 3
                 :n-record 4
                 :records '([2 2 2] [2 2 2] [2 2 2] [2 2 2])}
        adtree  (adtree/make data)
        learner (learner/alloc adtree example-params)]
    (is (almost= 0.0 (learner/score learner))))
  ; Case 2
  (let [data    {:n-var 3
                 :n-record 4
                 :records '([2 0 0] [2 0 1] [2 0 0] [2 0 1])}
        adtree  (adtree/make data)
        learner (learner/alloc adtree example-params)]
    (is (almost= -2.772589 (learner/score learner))))
  ; Case 3
  (let [data    {:n-var 3
                 :n-record 4
                 :records '([0 1 0] [1 0 1] [0 1 0] [1 0 1])}
        adtree  (adtree/make data)
        learner (learner/alloc adtree example-params)]
    (is (almost= -8.317766 (learner/score learner))))
  ; example-adtree from above
  (let [learner (learner/alloc example-adtree example-params)]
    (is (almost= -7.271270 (learner/score learner)))))

(deftest create-partition
  (are [min max i n expected] (= expected (@#'bayes.learner/create-partition min max i n))
    ; [0 1 2 3] in 4 partitions:
    0  4  0 4 (list 0)
    0  4  1 4 (list 1)
    0  4  2 4 (list 2)
    0  4  3 4 (list 3)
    ; [0 1 2 3] in 3 partitions:
    0  4  0 3 (list 0)
    0  4  1 3 (list 1)
    0  4  2 3 (list 2 3)
    ; [0 1 2 3] in 2 partitions:
    0  4  0 2 (list 0 1)
    0  4  1 2 (list 2 3)
    ; [0 1 2 3] in 1 partition:
    0  4  0 1 (list 0 1 2 3)))

(deftest run
  ; Note: results have been verified against C version.
  ; Simple data: records all "2"
  (let [data    {:n-var 3
                 :n-record 4
                 :records '([2 2 2] [2 2 2] [2 2 2] [2 2 2])}
        adtree  (adtree/make data)
        learner (learner/alloc adtree example-params)]
    (is (almost= 0.0 (learner/score learner)))
    (learner/run learner)
    (is (almost= 0.0 (learner/score learner))))
  ; Case 2
  (let [data    {:n-var 3
                 :n-record 4
                 :records '([2 0 0] [2 0 1] [2 0 0] [2 0 1])}
        adtree  (adtree/make data)
        learner (learner/alloc adtree example-params)]
    (is (almost= -2.772589 (learner/score learner)))
    (learner/run learner)
    (is (almost= -2.772589 (learner/score learner))))
  ; Case 3
  (let [data    {:n-var 3
                 :n-record 4
                 :records '([0 1 0] [1 0 1] [0 1 0] [1 0 1])}
        adtree  (adtree/make data)
        learner (learner/alloc adtree example-params)]
    (is (almost= -8.317766 (learner/score learner)))
    (learner/run learner)
    (is (almost= -4.158883 (learner/score learner))))
  ; example-adtree from above
  (let [learner (learner/alloc example-adtree example-params)]
    (is (almost= -7.271270 (learner/score learner)))
    (learner/run learner)
    (is (almost= -6.931472 (learner/score learner)))))
