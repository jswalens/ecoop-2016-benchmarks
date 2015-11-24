(ns log)

(def logger (agent nil))

(defn log [& msgs]
  (send logger (fn [_] (apply println msgs)))
  nil)
