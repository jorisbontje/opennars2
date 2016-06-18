(ns narjure.control-utils
  (:require
    [narjure.bag :as b]
    [narjure.defaults :refer [max-evidence]]
    [clojure.math.numeric-tower :as math]))

(defn round2
  "Round a double to the given precision (number of significant digits)"
  [precision d]
  (let [factor (Math/pow 10 precision)]
    (/ (Math/round (* d factor)) factor)))

(defn selection-fn-old
  ""
  [bag _]
  (let [count (b/count-elements bag)
        i (Math/abs (- (* (+ (rand) (rand)) count) count))]
    ;(println (str "i: " i " count: " count))
    i))

(defn selection-fn
  ""
  [bag param]
  (let [count (b/count-elements bag)
        i (- (Math/ceil (* (math/expt (rand) param) count)) 1)]
    ;(println (str "i: " i " count: " count))
    i))

(defn forget-element [el]
  (let [budget (:budget (:task el))
        new-priority (* (:priority el) (second budget))
        new-budget  [new-priority (second budget)]]
    (assoc el :priority new-priority
              :task (assoc (:task el) :budget new-budget))))

(defn make-ev-helper [e2 e1 sofar]
  (let [r1 (first e1)
        r2 (first e2)]
    (case [(= nil r1) (= nil r2)]
      [true true] sofar
      [true false] (make-ev-helper [] (rest e2) (concat [r2] sofar))
      [false true] (make-ev-helper (rest e1) [] (concat [r1] sofar))
      [false false] (make-ev-helper (rest e1) (rest e2) (concat [r1] [r2] sofar)))))

(defn make-evidence [e1 e2]
  (take max-evidence (reverse (make-ev-helper e1 e2 []))))

(defn non-overlapping-evidence? [e1 e2]
  (empty? (clojure.set/intersection (set e1) (set e2))))