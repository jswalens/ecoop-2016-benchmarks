(ns bayes.adtree-test
  (:require [clojure.test :refer :all]
            [bayes.adtree :as adtree]))

(def example-data-2
  {:n-var 2 :n-record 4 :records [[0 0] [0 1] [1 0] [1 1]]})

(def example-adtree-2
  (adtree/make example-data-2))

(def expected-adtree-2
  ; Retrieved from C version
  {:n-var 2
   :n-record 4
   :root-node
     {:index -1
      :value -1
      :count 4
      :vary-vector (list
        {:index 0
         :most-common-value 0
         :zero-node nil
         :one-node
           {:index 0
            :value 1
            :count 2
            :vary-vector (list
              {:index 1
               :most-common-value 0
               :zero-node nil
               :one-node
                 {:index 1
                  :value 1
                  :count 1
                  :vary-vector (list)}})}}
        {:index 1
         :most-common-value 0
         :zero-node nil
         :one-node
           {:index 1
            :value 1
            :count 2
            :vary-vector (list)}})}})

(def example-data-3
  {:n-var 3 :n-record 4 :records [[1 0 0] [1 0 1] [0 0 0] [0 1 0]]})

(def example-adtree-3
  (adtree/make example-data-3))

(def expected-adtree-3
  ; Retrieved from C version
  {:n-var 3
   :n-record 4
   :root-node
     {:index -1
       :value -1
       :count 4
       :vary-vector (list
         {:index 0
          :most-common-value 0
          :zero-node nil
          :one-node
            {:index 0
             :value 1
             :count 2
             :vary-vector (list
               {:index 1
                :most-common-value 0
                :zero-node nil
                :one-node nil}
               {:index 2
                :most-common-value 0
                :zero-node nil
                :one-node
                  {:index 2
                   :value 1
                   :count 1
                   :vary-vector (list)}})}}
         {:index 1
          :most-common-value 0
          :zero-node nil
          :one-node
            {:index 1
             :value 1
             :count 1
             :vary-vector (list
               {:index 2
                :most-common-value 0
                :zero-node nil
                :one-node nil})}}
         {:index 2
          :most-common-value 0
          :zero-node nil
          :one-node
            {:index 2
             :value 1
             :count 1
             :vary-vector (list)}})}})

(def example-data-4
  {:n-var 3 :n-record 2 :records [[0 0 0] [1 1 1]]})

(def example-adtree-4
  (adtree/make example-data-4))

(def expected-adtree-4
  ; Retrieved from C version
  {:n-var 3
   :n-record 2
   :root-node
     {:index -1
      :value -1
      :count 2
      :vary-vector (list
        {:index 0
         :most-common-value 0
         :zero-node nil
         :one-node
           {:index 0
            :value 1
            :count 1
            :vary-vector (list
              {:index 1
               :most-common-value 1
               :zero-node nil
               :one-node nil}
              {:index 2
               :most-common-value 1
               :zero-node nil
               :one-node nil})}}
        {:index 1
         :most-common-value 0
         :zero-node nil
         :one-node
           {:index 1
            :value 1
            :count 1
            :vary-vector (list
              {:index 2
               :most-common-value 1
               :zero-node nil
               :one-node nil})}}
        {:index 2
         :most-common-value 0
         :zero-node nil
         :one-node
           {:index 2
            :value 1
            :count 1
            :vary-vector (list)}})}})

(deftest make
  (is (= expected-adtree-2 example-adtree-2))
  (is (= expected-adtree-3 example-adtree-3))
  (is (= expected-adtree-4 example-adtree-4)))

; Note: @#'bayes.adtree/drop-one is a trick to get the private function drop-one

(deftest drop-one
  (are [coll i expected] (= expected (@#'bayes.adtree/drop-one i coll))
    [0 1 2] 0 [1 2]
    [0 1 2] 1 [0 2]
    [0 1 2] 2 [0 1]
    [0 1 2] 3 [0 1 2]
    []      0 []))

(deftest swap-bit
  (is (= 0 (@#'bayes.adtree/swap-bit 1)))
  (is (= 1 (@#'bayes.adtree/swap-bit 0))))

(deftest get-count
  ; These results have been verified against the C version.
  (is (= 1 (adtree/get-count example-adtree-2
    [{:index 0 :value 0} {:index 1 :value 0}]
    [0 1])))
  (is (= 1 (adtree/get-count example-adtree-2
    [{:index 0 :value 0} {:index 1 :value 1}]
    [0 1])))
  (is (= 1 (adtree/get-count example-adtree-2
    [{:index 0 :value 1} {:index 1 :value 0}]
    [0 1])))
  (is (= 1 (adtree/get-count example-adtree-2
    [{:index 0 :value 1} {:index 1 :value 1}]
    [0 1])))
  (is (= 2 (adtree/get-count example-adtree-3
    [{:index 0 :value 0}]
    [0])))
  (is (= 2 (adtree/get-count example-adtree-3
    [{:index 0 :value 1}]
    [0])))
  (is (= 1 (adtree/get-count example-adtree-3
    [{:index 0 :value 0} {:index 1 :value 0}]
    [0 1])))
  (is (= 1 (adtree/get-count example-adtree-3
    [{:index 0 :value 0} {:index 1 :value 1}]
    [0 1])))
  (is (= 2 (adtree/get-count example-adtree-3
    [{:index 0 :value 1} {:index 1 :value 0}]
    [0 1])))
  (is (= 0 (adtree/get-count example-adtree-3
    [{:index 0 :value 1} {:index 1 :value 1}]
    [0 1])))
  ; The following caused an error in the code (super count <= invert count)
  (is (= 0 (adtree/get-count example-adtree-4
    [{:index 0 :value 0} {:index 1 :value 1} {:index 2 :value 0}]
    [0 1 2]))))
