(ns bayes.learner
  (:require [bayes.options :as options]
            [bayes.net :as net]
            [bayes.adtree :as adtree]
            [log :refer [log]]
            [taoensso.timbre.profiling :refer [p defnp]]))

(defmacro for-all [seq-exprs body-expr]
  `(doall
    (for ~seq-exprs
      ~body-expr)))

(defmacro parallel-for-all [seq-exprs body-expr]
  `(map deref
    (doall
      (for ~seq-exprs
        (future ~body-expr)))))

;
; alloc
;

(defn alloc [adtree params]
  "Allocate the learner.

  We have an extra argument `params` to get some global parameters.

  The C version has tasks, an array containing tasks, and taskList, an ordered
  list of pointers to tasks (ordered by score). We only have tasks, as an
  ordered list of tasks.

  In the C version, parts of this struct are aligned to cache lines. We don't
  care about that here."
  {:adtree                     adtree
   ; Net, containing refs:
   :net                        (net/alloc (:n-var adtree))
   ; Global variables:
   :local-base-log-likelihoods (ref (vec (repeat (:n-var adtree) 0.0)))
   :base-log-likelihood        (ref 0.0)
   :n-total-parent             (ref 0)
   ; Shared task queue:
   :tasks                      (ref []) ; sorted by score
   ; Parameters:
   :n-thread                   (:thread params)
   :max-num-edge-learned       (:edge params)
   :insert-penalty             (:insert params)
   :operation-quality-factor   (:quality params)})

;
; Helper functions to manage shared variables
;

(defn- get-local-base-log-likelihood [learner i]
  "Get i'th local-base-log-likelihood in `learner`."
  (nth @(:local-base-log-likelihoods learner) i))

(defn- set-local-base-log-likelihood [learner i v]
  "Set i'th local-base-log-likelihood in `learner` to `v`."
  (alter (:local-base-log-likelihoods learner) assoc i v))

;
; Helper functions to manage tasks
;

(defn- add-task [tasks task]
  "Add `task` to `tasks`, ordered by descending score."
  (dosync
    (ref-set tasks (vec (reverse (sort-by :score (conj @tasks task)))))))

(defn- add-tasks [tasks new-tasks]
  "Add `new-tasks` to `tasks`, ordered by descending score."
  (dosync
    ; reversing new-tasks ensures scores with equal size get taken in same order
    ; they were inserted.
    (ref-set tasks (vec (reverse (sort-by :score (concat @tasks (reverse new-tasks))))))))

(defn- pop-task [tasks]
  "Returns first element of `tasks`, and pops that element from `tasks`. Returns
  nil if tasks is empty."
  (dosync
    (if (empty? @tasks)
      nil
      (let [v (first @tasks)]
        (alter tasks rest)
        v))))

;
; Queries
;
; queries are a vector of maps {:index ... :value ...}, e.g:
; [{:index 0 :value -1} {:index 1 :value 1} {:index 2 :value 0} ...]
; where :index of query i is normally[*] always i, and value is either 0, 1,
; or QUERY_VALUE_WILDCARD (-1).
; This corresponds to the queries of the C version, which is an array of
; structs.
;
; [*] There are exceptions to this, notably in create-task.

(def ^:const QUERY_VALUE_WILDCARD -1)

(defn- create-queries [n-var]
  (vec
    (for [v (range n-var)]
      {:index v :value QUERY_VALUE_WILDCARD})))

(defn- set-query-value [queries index value]
  "Set value of query at `index` in `queries` to `value`."
  (assoc-in queries [index :value] value))

;
; Query vector & parent query vector
;
; In the C version, a query-vector or a parent-query-vector is a vector of
; pointers to a query. Therefore, changing a query's value is reflected in all
; (parent-)query-vectors in which it is included.
; In the Clojure version, a (parent-)query-vector is a list of indices. This
; means that whenever we want to retrieve a query from a (parent-)query-vector,
; we have an extra bit of indirection.

(defn- populate-parent-query-vector [net id]
  "Returns parent-query-vector for node with `id`.
  Should be called in transaction."
  (net/parent-ids net id))

(defn- populate-query-vectors [net id]
  "Returns {:query-vector ... :parent-query-vector ...} for node with `id`.
  Should be called in transaction."
  (let [parent-query-vector (populate-parent-query-vector net id)
        query-vector        (sort (conj parent-query-vector id))]
    {:query-vector query-vector :parent-query-vector parent-query-vector}))

;
; Functions to compute (specific) local (base) log likelihood
;

(defn- compute-specific-local-log-likelihood [adtree queries query-vector parent-query-vector]
  (let [count (adtree/get-count adtree queries query-vector)]
    (if (= count 0)
      0.0
      (let [probability  (/ (double count) (double (:n-record adtree)))
            parent-count (adtree/get-count adtree queries parent-query-vector)]
        (* probability (double (Math/log (/ (double count) (double parent-count)))))))))

(defn- compute-local-log-likelihood-helper [i adtree queries query-vector parent-query-vector]
  (if (>= i (count parent-query-vector))
    (compute-specific-local-log-likelihood adtree queries query-vector parent-query-vector)
    (+ (compute-local-log-likelihood-helper (inc i) adtree
         (set-query-value queries (nth parent-query-vector i) 0)
         query-vector parent-query-vector)
       (compute-local-log-likelihood-helper (inc i) adtree
         (set-query-value queries (nth parent-query-vector i) 1)
         query-vector parent-query-vector))))

(defn- compute-local-log-likelihood [id adtree queries query-vector parent-query-vector]
  (+ (compute-local-log-likelihood-helper 0 adtree (set-query-value queries id 0)
       query-vector parent-query-vector)
     (compute-local-log-likelihood-helper 0 adtree (set-query-value queries id 1)
       query-vector parent-query-vector)))

(defn- compute-local-base-log-likelihoods [vars adtree]
  ; The C version has queries = [X, Y]; queryVector = [&X];
  ; parentQuery = Z; and parentQueryVector = [].
  ; In Clojure, we have queries = [X]; query-vector = [0];
  ; parent-query-vector = []
  (vec
    (for [v vars]
      (+ (compute-specific-local-log-likelihood adtree [{:index v :value 0}] [0] [])
         (compute-specific-local-log-likelihood adtree [{:index v :value 1}] [0] [])))))

;
; score
;

(defn- sum [ns]
  "Sums `ns`."
  (reduce + ns))

(defn- calculate-score [learner penalty-factor log-likelihood]
  "Calculate the score, based on the number of records in `learner` and the
  `penalty-factor` and `log-likelihood`."
  (let [n-record       (:n-record (:adtree learner))
        base-penalty   (* -0.5 (Math/log (double n-record)))]
    (+ (* base-penalty penalty-factor) (* n-record log-likelihood))))

(defn score [learner]
  "Score learner."
  (let [n-var   (:n-var (:adtree learner))
        queries (create-queries n-var)
        n-total-parent
          (dosync
            (sum
              (for [v (range n-var)]
                (count (net/parent-ids (:net learner) v)))))
        log-likelihood
          (sum
            ; Following for can take a long time: optimizable using parallel-for-all
            (for [v (range n-var)]
              (let [{query-vector :query-vector parent-query-vector :parent-query-vector}
                      (populate-query-vectors (:net learner) v)]
                (compute-local-log-likelihood
                  v
                  (:adtree learner)
                  queries
                  query-vector
                  parent-query-vector))))]
    (calculate-score learner n-total-parent log-likelihood)))

;
; run
;

(defn- create-partition [minimum maximum i n]
  "Given a range from `minimum` to `maximum`, returns the subrange for chunk `i`
  out of `n` total chunks."
  (let [size  (- maximum minimum)                  ; total range
        chunk (max 1 (quot (+ size (quot n 2)) n)) ; size of 1 chunk
        start (+ minimum (* chunk i))              ; start of this chunk
        stop  (if (= i (dec n))                    ; end of this chunk
                maximum
                (min maximum (+ start chunk)))]
    (range start stop)))

(defn- create-task [v learner base-log-likelihood this-local-log-likelihood]
  "Create and return task for variable `v`, or nil if no better local log
  likelihood exists."
  ; The C version has queries = [X, Y]; queryVector = [&X, &Y];
  ; parentQuery = Z; and parentQueryVector = [&Z].
  ; In Clojure, we have queries = [X, Y, Z]; query-vector = [0, 1];
  ; parent-query-vector = [2]
  (let [adtree (:adtree learner)
        ; A. Find best local index
        ; A.1 find local-log-likelihood for every variable except v
        other-local-log-likelihoods
          (for [vv (range (:n-var adtree))
                :when (not= vv v)]
            (let [initial-queries
                    [{:index vv :value nil}
                     {:index vv :value nil}
                     {:index vv :value nil}]
                  queries
                    (assoc-in initial-queries [(if (< v vv) 0 1) :index] v)
                  query-vector        [0 1]
                  parent-query-vector [2]
                  other-local-log-likelihood
                    (+
                      (compute-specific-local-log-likelihood adtree
                        (-> queries
                          (assoc-in [0 :value] 0)
                          (assoc-in [1 :value] 0)
                          (assoc-in [2 :value] 0))
                        query-vector parent-query-vector)
                      (compute-specific-local-log-likelihood adtree
                        (-> queries
                          (assoc-in [0 :value] 0)
                          (assoc-in [1 :value] 1)
                          (assoc-in [2 :value] (if (< vv v) 0 1)))
                        query-vector parent-query-vector)
                      (compute-specific-local-log-likelihood adtree
                        (-> queries
                          (assoc-in [0 :value] 1)
                          (assoc-in [1 :value] 0)
                          (assoc-in [2 :value] (if (< vv v) 1 0)))
                        query-vector parent-query-vector)
                      (compute-specific-local-log-likelihood adtree
                        (-> queries
                          (assoc-in [0 :value] 1)
                          (assoc-in [1 :value] 1)
                          (assoc-in [2 :value] 1))
                        query-vector parent-query-vector))]
              {:index vv :value other-local-log-likelihood}))
        ; A.2 Sort them and take the one with the highest value. If there are
        ; several with the same value, take the one with the lowest index, for
        ; compatibility with the C version.
        best-local-value
          (->> other-local-log-likelihoods
            (sort-by :value)
            (last)
            (:value))
        best-local-index
          (->> other-local-log-likelihoods
            (filter #(= (:value %) best-local-value))
            (sort-by :index)
            (first)
            (:index))]
    (if (> best-local-value this-local-log-likelihood)
      {:op      :insert
       :from-id best-local-index
       :to-id   v
       :score   (calculate-score learner 1.0
                  (+ base-log-likelihood
                     best-local-value
                     (- this-local-log-likelihood)))}
      nil)))

(defnp create-tasks [learner i n]
  "Create tasks and add them to learner. This is thread `i` of `n`."
  (let [adtree                     (:adtree learner)
        vars                       (create-partition 0 (:n-var adtree) i n)
                                   ; subset of variables for this thread
        local-base-log-likelihoods (compute-local-base-log-likelihoods vars adtree)
        base-log-likelihood        (sum local-base-log-likelihoods)]
    (dosync
      (doseq [j (range (count vars))]
        (set-local-base-log-likelihood learner
          (nth vars j) (nth local-base-log-likelihoods j)))
      (alter (:base-log-likelihood learner) + base-log-likelihood))
    (let [tasks (filter some?
                  (map-indexed
                    (fn [v_i v]
                      (create-task v learner base-log-likelihood
                        (nth local-base-log-likelihoods v_i)))
                    vars))]
          ; TODO: maybe doall to force execution before tx?
      (log "tasks created by thread" i ":" tasks)
      (add-tasks (:tasks learner) tasks))))

(defnp is-task-valid? [task net]
  (let [from (:from-id task)
        to   (:to-id task)]
    (case (:op task)
      :insert  (not (or (net/has-edge? net from to)
                        (net/has-path?  net to from)))
      :remove  true ; can never create cycle, so always valid
      :reverse (dosync
                 (net/remove-edge net from to) ; temp remove edge for check
                 (let [valid? (not (net/has-path? net from to))]
                   (net/insert-edge net from to)
                   valid?))
      (log "ERROR: unknown task operation type" (:op task)))))

(defnp apply-task [task net]
  "Apply `task` to `net`. Updates `net`."
  ((case (:op task)
    :insert  net/insert-edge
    :remove  net/remove-edge
    :reverse net/reverse-edge)
    net (:from-id task) (:to-id task)))

(defnp calculate-delta-log-likelihood [task learner]
  "Returns delta-log-likelihood, and sets the local-base-log-likelihoods and
  n-total-parent of the learner."
  (let [adtree (:adtree learner)
        to     (:to-id task)]
    (case (:op task)
      :insert
        (dosync
          (let [queries (create-queries (:n-var adtree))
                {query-vector :query-vector parent-query-vector :parent-query-vector}
                  (populate-query-vectors (:net learner) to)
                new-base-log-likelihood
                  (compute-local-log-likelihood to adtree
                    queries query-vector parent-query-vector)
                to-local-base-log-likelihood
                  (get-local-base-log-likelihood learner to)
                delta-log-likelihood
                  (- to-local-base-log-likelihood new-base-log-likelihood)]
            (set-local-base-log-likelihood learner to new-base-log-likelihood)
            ; The following happens in a separate tx in the C version, we use
            ; commute to avoid conflicts
            (commute (:n-total-parent learner) inc)
            delta-log-likelihood))
      (log "ERROR: unknown task operation type" (:op task)))))

(defn- compare-higher [a b]
  "A comparator returning +1 if a < b, 0 if a = b, and -1 if a > b, the opposite
  of the normal compare."
  (compare b a))

(defnp find-best-insert-task [learner to-id n-total-parent base-log-likelihood]
  "Finds a task that inserts an edge into the net, such that the local log
  likelihood is maximally increased. Returns this task, or nil if none was
  found."
  (let [net    (:net learner)
        adtree (:adtree learner)]
    (p :find-best-insert-task-out-tx
      (dosync
        (p :find-best-insert-task-in-tx
          (let [parent-ids           (net/parent-ids net to-id)
                max-num-edge-learned (:max-num-edge-learned learner)]
            (if (or (< max-num-edge-learned 0)
                    (<= (count parent-ids) max-num-edge-learned))
              (let [; Search all possible valid operations for better local log
                    ; likelihood
                    invalid-ids
                      ; Don't search any descendant, immediate parents, or self
                      (p :1-invalid
                        (-> (net/find-descendants net to-id)
                          (into parent-ids)
                          (conj to-id)))
                    queries
                      (p :2-queries (create-queries (:n-var adtree)))
                    {query-vector :query-vector parent-query-vector :parent-query-vector}
                      (p :3-query-vectors (populate-query-vectors net to-id))
                    alternative-local-log-likelihoods
                      (p :4-alternatives
                        (if (options/variation? :alternatives-parallel)
                          (parallel-for-all [from-id (range (:n-var adtree))
                                    :when (not (.contains invalid-ids from-id))]
                            {:from-id from-id
                             :local-log-likelihood
                               (compute-local-log-likelihood
                                 to-id
                                 adtree
                                 queries
                                 (sort (conj query-vector from-id))
                                 (sort (conj parent-query-vector from-id)))})
                          (for-all [from-id (range (:n-var adtree))
                                    :when (not (.contains invalid-ids from-id))]
                            {:from-id from-id
                             :local-log-likelihood
                               (compute-local-log-likelihood
                                 to-id
                                 adtree
                                 queries
                                 (sort (conj query-vector from-id))
                                 (sort (conj parent-query-vector from-id)))})))
                    old-local-log-likelihood
                      (p :5-old
                        (get-local-base-log-likelihood learner to-id))
                    {best-from-id :from-id best-local-log-likelihood :local-log-likelihood}
                      (p :6-best
                        (->>
                          (conj alternative-local-log-likelihoods
                            ; or, nothing happens:
                            {:from-id to-id :local-log-likelihood old-local-log-likelihood})
                          (sort-by :local-log-likelihood compare-higher)
                          (first)))] ; find one with highest local-log-likelihood
                (if (= best-from-id to-id)
                  nil ; best to do nothing
                  (let [n-parent-new (inc (count parent-ids))
                        n-total-parent-new
                          (+ n-total-parent
                             (* n-parent-new (:insert-penalty learner)))
                        log-likelihood
                          (+ base-log-likelihood
                             best-local-log-likelihood
                             (- old-local-log-likelihood))]
                    {:op      :insert
                     :from-id best-from-id
                     :to-id   to-id
                     :score   (calculate-score learner
                                n-total-parent-new
                                log-likelihood)})))
              nil)))))))

(defnp find-next-task [learner n-total-parent base-log-likelihood to-id]
  "Find best next task, or nil if none was found."
  (let [base-score (calculate-score learner n-total-parent base-log-likelihood)
        new-task   (find-best-insert-task learner to-id n-total-parent
                     base-log-likelihood)
        oqf        (:operation-quality-factor learner)]
    (if (and (some? new-task)
             (> (:score new-task) (/ base-score oqf)))
      new-task
      nil)))

(defnp process-task [task learner i]
  "Process the `task`.

  This is thread `i` (used for logging)."
  (let [valid?
          (dosync ; Updates the net
            (if (is-task-valid? task (:net learner))
              ; If task is still valid, update graph and probabilities
              (do
                (apply-task task (:net learner))
                true)
              false))
        _ (log "task processed by thread" i ":" task
            (if valid? "(valid)" "(invalid)"))
        delta-log-likelihood
          (if valid?
            (calculate-delta-log-likelihood task learner)
            0.0)
        ; Update/read globals
        [base-log-likelihood n-total-parent]
          (dosync
            [(alter (:base-log-likelihood learner) + delta-log-likelihood)
             @(:n-total-parent learner)])
        ; Find next task
        best-task
          (find-next-task learner n-total-parent base-log-likelihood
            (:to-id task))]
    (when (some? best-task)
      (log "new task on thread" i ":" best-task)
      (add-task (:tasks learner) best-task))))

(defnp learn-structure [learner i n]
  "Learn the structure of the network, thread `i` of `n`."
  (loop []
    (let [task (pop-task (:tasks learner))]
      (when (not (nil? task))
        (process-task task learner i)
        (recur)))))

(defnp run [learner]
  "Learn structure of the network, updates it."
  (let [n-thread    (:n-thread learner)
        par-map     (fn [f] (map (fn [i] (future (f i))) (range n-thread)))
        par-deref   (fn [futs] (doseq [f futs] (deref f)))
        create-futs (par-map #(create-tasks    learner % n-thread))
        learn-futs  (par-map #(learn-structure learner % n-thread))]
    (par-deref create-futs)
    (par-deref learn-futs)))
