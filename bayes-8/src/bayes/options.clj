(ns bayes.options
  (:require [clojure.tools.cli]
            [clojure.string]
            [taoensso.timbre :as timbre]))

; Variations to enable.
; Possible variations:
; * alternatives-parallel: in learner/find-best-insert-task, calculate the
;   alternatives in parallel.
; * strict-adtree: in adtree/make-vary, do not generate the adtree lazily, but
;   strictly.
(def variations (atom []))

(defn variation? [variation]
  "Is `variation` enabled?"
  (.contains @variations variation))

  (def cli-params
    [["-e" "--edge UINT"    "Max [e]dges learned per variable (-1 for no limit)"
      :default -1
      :parse-fn #(Integer/parseInt %)]
     ["-i" "--insert UINT"  "Edge [i]nsert penalty"
      :default 1
      :parse-fn #(Integer/parseInt %)]
     ["-n" "--number UINT"  "Max [n]umber of parents"
      :default 4
      :parse-fn #(Integer/parseInt %)]
     ["-p" "--percent UINT" "[p]ercent chance of parent"
      :default 10
      :parse-fn #(Integer/parseInt %)]
     ["-q" "--quality FLT"  "Operation [q]uality factor"
      :default 1.0
      :parse-fn #(Double/parseDouble %)]
     ["-r" "--record UINT"  "Number of [r]ecords"
      :default 256
      :parse-fn #(Integer/parseInt %)]
     ["-s" "--seed UINT"    "Random [s]eed"
      :default 1
      :parse-fn #(Integer/parseInt %)]
     ["-t" "--thread UINT"  "Number of [t]hreads"
      :default 1
      :parse-fn #(Integer/parseInt %)]
     ["-v" "--var UINT"     "Number of [v]ariables"
      :default 16
      :parse-fn #(Integer/parseInt %)]
     ["-h" "--help"]
     ["-x" "--variations VARIATIONS" "Comma-separated list of variations to enable."
      :default  []
      :parse-fn #(map keyword (clojure.string/split % #","))]
     ["-m" "--profile"      "Enable profiling"
      :default false]])

  (def c-params
    ; Default parameters of C version
    ; Takes a long time
    {:edge    -1   ; -1 means no limit
     :insert  1
     :number  4
     :percent 10
     :quality 1.0
     :record  4096
     :seed    1
     :thread  1
     :var     32})

  (def fast-params
    ; Used for testing
    ; In the C version, the default for :record is 4096 and for :var 32. However,
    ; adtree/make takes > 500 seconds on my machine for these values. For 256
    ; records and 16 variables this is only 0.4s.
    {:edge    -1   ; -1 means no limit
     :insert  1
     :number  4
     :percent 10
     :quality 1.0
     :record  256
     :seed    1
     :thread  1
     :var     16})

  (def paper-normal
    ; "bayes" benchmark in STAMP paper
    {:edge    2
     :insert  2
     :number  2
     :percent 20
     :quality 1.0
     :record  1024
     :seed    1
     :thread  1
     :var     32})

  (def paper-larger
    ; "bayes+" benchmark in STAMP paper
    {:edge    2
     :insert  2
     :number  2
     :percent 20
     :quality 1.0
     :record  4096
     :seed    1
     :thread  1
     :var     32})

  (def paper-largest
    ; "bayes++" benchmark in STAMP paper
    ; Takes a really long time
    {:edge    8
     :insert  2
     :number  10
     :percent 40
     :quality 1.0
     :record  4096
     :seed    1
     :thread  1
     :var     32})

(def usage
  (str
"Usage: ./bayes [options]

Options:

" (:summary (clojure.tools.cli/parse-opts nil cli-params))))

(defn set-args [args]
 "Parse, set, and print the command line arguments.

 Ignores errors."
 (let [{:keys [options errors]} (clojure.tools.cli/parse-opts args cli-params)]
   (when (not-empty errors)
     (println "ERROR: Error when parsing command line arguments: "
       errors))

   (when (or (not-empty errors) (:help options))
     (println usage))

   (random/set-seed (:seed options))
   (reset! variations (:variations options))
   (timbre/set-level! (if (:profile options) :trace :error))

   (println "Random seed                =" (:seed options))
   (println "Number of vars             =" (:var options))
   (println "Number of records          =" (:record options))
   (println "Max num parents            =" (:number options))
   (println "% chance of parent         =" (:percent options))
   (println "Insert penalty             =" (:insert options))
   (println "Max num edge learned / var =" (:edge options))
   (println "Operation quality factor   =" (:quality options))
   (println "Variations                 =" (:variations options))
   (println "Profiling?                 =" (:profile options))

   options))
