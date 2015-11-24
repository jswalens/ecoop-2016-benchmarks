(ns bayes.data-test
  (:require [clojure.test :refer :all]
            [random]
            [bayes.options :as options]
            [bayes.data :as data]))

; Note: @#'bayes.data/bits->bitmap is a trick to get the private function
; bits->bitmap.

(deftest bits->bitmap
  (are [expected in] (= expected (@#'bayes.data/bits->bitmap in))
    2r0   (list 0)
    2r1   (list 1)
    2r10  (list 1 0)
    2r100 (list 1 0 0)
    2r101 (list 1 0 1)
    2r1   (list 0 0 1)
    2r10  (list 0 1 0)))

(deftest concat-uniq
  (are [xs ys expected] (= expected (@#'bayes.data/concat-uniq xs ys))
    [1 2 3] [4 5 6] [1 2 3 4 5 6]
    [1 2 3] [1 5 6] [1 2 3 5 6]
    [1 2 3] [1 3 3] [1 2 3]
    []      [4 5 6] [4 5 6]
    [1 2 3] []      [1 2 3]))

(defn- deref-net [net]
  (map deref net))

(def generated-data-1
  {:n-var 3
   :n-record 4
   :records '([1 0 0] [1 0 1] [0 0 0] [0 1 0])})

(def generated-net-1
  [{:id 0 :parent-ids '()  :child-ids '()}
   {:id 1 :parent-ids '(2) :child-ids '()}
   {:id 2 :parent-ids '()  :child-ids '(1)}])

(deftest generate-1
  (random/set-seed 1)
  (let [params (assoc options/fast-params :record 4 :var 3)
        {data :data net :net} (data/generate params)]
    (is (= generated-data-1 data))
    (is (= generated-net-1 (deref-net net)))))

(def generated-net-2
  [{:id 0  :parent-ids '(8 13 14 15)        :child-ids '(7 9 11)}
   {:id 1  :parent-ids '(10 14 21 26 28 29) :child-ids '(27)}
   {:id 2  :parent-ids '(9)                 :child-ids '(6)}
   {:id 3  :parent-ids '(23)                :child-ids '(4 10 24)}
   {:id 4  :parent-ids '(3 10 15 22)        :child-ids '()}
   {:id 5  :parent-ids '(21)                :child-ids '()}
   {:id 6  :parent-ids '(2 9)               :child-ids '()}
   {:id 7  :parent-ids '(0 15 17)           :child-ids '()}
   {:id 8  :parent-ids '(25)                :child-ids '(0 24)}
   {:id 9  :parent-ids '(0 18 19 20 24)     :child-ids '(2 6 12)}
   {:id 10 :parent-ids '(3 11 22)           :child-ids '(1 4)}
   {:id 11 :parent-ids '(0 15)              :child-ids '(10)}
   {:id 12 :parent-ids '(9 18 20 23 26)     :child-ids '()}
   {:id 13 :parent-ids '(16 29)             :child-ids '(0 19)}
   {:id 14 :parent-ids '(31)                :child-ids '(0 1 22 28)}
   {:id 15 :parent-ids '(19 23 30)          :child-ids '(0 4 7 11)}
   {:id 16 :parent-ids '(17 24)             :child-ids '(13 19)}
   {:id 17 :parent-ids '(20 24 31)          :child-ids '(7 16)}
   {:id 18 :parent-ids '(20 24 25)          :child-ids '(9 12)}
   {:id 19 :parent-ids '(13 16 25 29)       :child-ids '(9 15)}
   {:id 20 :parent-ids '(23)                :child-ids '(9 12 17 18)}
   {:id 21 :parent-ids '(26 28)             :child-ids '(1 5 24 25)}
   {:id 22 :parent-ids '(14)                :child-ids '(4 10 23)}
   {:id 23 :parent-ids '(22 25)             :child-ids '(3 12 15 20 27)}
   {:id 24 :parent-ids '(3 8 21)            :child-ids '(9 16 17 18 27)}
   {:id 25 :parent-ids '(21 26)             :child-ids '(8 18 19 23 30)}
   {:id 26 :parent-ids '()                  :child-ids '(1 12 21 25)}
   {:id 27 :parent-ids '(1 23 24 31)        :child-ids '()}
   {:id 28 :parent-ids '(14)                :child-ids '(1 21)}
   {:id 29 :parent-ids '(30)                :child-ids '(1 13 19)}
   {:id 30 :parent-ids '(25)                :child-ids '(15 29)}
   {:id 31 :parent-ids '()                  :child-ids '(14 17 27)}])

(deftest generate-2
  (random/set-seed 5)
  (let [params {:number 10 :percent 30 :record 512 :var 32}
        {data :data net :net} (data/generate params)]
    (is (= generated-net-2 (deref-net net)))))

(def generated-data-3
  {:n-var 32
   :n-record 4
   :records '([0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 1 0 0 0 0]
              [0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 1 0 0 0 0]
              [0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 1 0 0 1 0 0 0 0]
              [0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 1 0 0 0 0])})

(deftest generate-3
  (random/set-seed 1)
  (let [params {:number 10 :percent 30 :record 4 :var 32}
        {data :data net :net} (data/generate params)]
    (is (= generated-data-3 data))))

(deftest compare-record
  (are [a b offset expected] (= expected (@#'bayes.data/compare-record a b offset))
    [0 0 0] [0 0 0] 0 0  ; equal
    [0 0 0] [1 0 0] 0 -1 ; smaller
    [1 0 0] [0 0 0] 0 +1 ; larger
    [1 0 0] [1 0 0] 0 0
    [0 0 0] [0 0 1] 0 -1
    [0 0 0] [0 0 1] 1 -1
    [0 0 0] [0 0 1] 2 -1
    [0 0 0] [0 1 0] 0 -1
    [0 0 0] [0 1 0] 1 -1
    [0 0 0] [0 1 0] 2 0
    [0 0 0] [1 0 0] 0 -1
    [0 0 0] [1 0 0] 1 0
    [0 0 0] [1 0 0] 2 0))

(def sort-cases
  [; records              start n offset  result
   [[[0 1] [1 1] [1 0] [0 0]] 0 4 0 (list [0 0] [0 1] [1 0] [1 1])]
   ; change offset:
   [[[0 1] [1 1] [1 0] [0 0]] 0 4 1 (list [1 0] [0 0] [0 1] [1 1])]
   ; change start (records 1, 2, 3):
   [[[0 1] [1 1] [1 0] [0 0]] 1 3 0 (list [0 1] [0 0] [1 0] [1 1])]
   ; change n (records 0, 1):
   [[[0 1] [1 1] [1 0] [0 0]] 0 2 0 (list [0 1] [1 1] [1 0] [0 0])]
   ; change start and n (records 1, 2):
   [[[0 1] [1 1] [1 0] [0 0]] 1 2 0 (list [0 1] [1 0] [1 1] [0 0])]
   ; change start, n (records 1, 2), and offset:
   [[[0 1] [0 1] [1 0] [0 0]] 1 2 1 (list [0 1] [1 0] [0 1] [0 0])]])

(deftest sort-records
  (doseq [[records start n offset result] sort-cases]
    (is (= result (@#'bayes.data/sort-records records start n offset)))))

(deftest sort
  (doseq [[records start n offset result] sort-cases]
    (is (= {:n-var 2 :n-record 4 :records result}
      (bayes.data/sort {:n-var 2 :n-record 4 :records records} start n offset)))))

(deftest find-split
  (are [data start n offset expected] (= expected (bayes.data/find-split data start n offset))
    {:records [[0 0] [0 1] [1 0] [1 1]]} 0 4 0  2 ; first two start with 0, next two start with 1
    ; change offset:
    {:records [[1 0] [0 0] [0 1] [1 1]]} 0 4 1  2 ; first two have 0, next two have 1, at offset 1
    ; change start (records 1, 2, 3):
    {:records [[0 1] [0 0] [1 0] [1 1]]} 1 3 0  1 ; first one start with 0, next two start with 1
    ; change n (records 0, 1):
    {:records [[0 1] [1 1] [1 0] [0 0]]} 0 2 0  1 ; first one starts with 0, next one starts with 1
    ; change start and n (records 1, 2):
    {:records [[0 1] [1 0] [1 1] [0 0]]} 1 2 0  0 ; none start with zero, two start with 1
    ; change start, n (records 1, 2), and offset:
    {:records [[0 1] [1 0] [0 1] [0 0]]} 1 2 1  1)) ; first one has zero, next one has 1, at offset 1
